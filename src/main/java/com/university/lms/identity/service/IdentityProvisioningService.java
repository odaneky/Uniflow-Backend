package com.university.lms.identity.service;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.domain.IdentityErrorCode;
import com.university.lms.identity.domain.User;
import com.university.lms.identity.dto.ProvisionIdentityRequest;
import com.university.lms.identity.dto.UserResponse;
import com.university.lms.identity.repository.UserRepository;
import com.university.lms.identity.spi.IdentityAccount;
import com.university.lms.identity.spi.IdentityProvider;
import com.university.lms.identity.spi.NewIdentityAccount;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrative operations that span the identity provider and this application's local record.
 *
 * <p>Separate from {@link UserService}, which only reads and only touches local rows. The split is
 * the point: everything here has an effect outside UniFlow, so it is where ordering, failure
 * handling and auditing have to be thought about explicitly.
 *
 * <h2>Ordering</h2>
 *
 * <p>The identity provider is changed <b>first</b>, then the local projection. If the local write
 * then fails, the provider has still applied the change and the projection is stale — which is
 * recoverable by re-reading. The other order is not: a local row saying "suspended" while the
 * account still authenticates is a security control that reports success and does nothing, and it
 * is exactly the failure this service exists to remove.
 *
 * <p>There is no distributed transaction here and there should not be one. The rule is: make the
 * authoritative change first, record it, and let reconciliation fix the projection.
 */
@Service
public class IdentityProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(IdentityProvisioningService.class);

    private static final String ENTITY = "User";

    private final IdentityProvider identityProvider;
    private final UserRepository userRepository;
    private final AuditTrail auditTrail;
    private final CurrentUserProvider currentUserProvider;
    private final IdentityOutboxPublisher identityOutboxPublisher;

    public IdentityProvisioningService(
            IdentityProvider identityProvider,
            UserRepository userRepository,
            AuditTrail auditTrail,
            CurrentUserProvider currentUserProvider,
            IdentityOutboxPublisher identityOutboxPublisher) {
        this.identityProvider = identityProvider;
        this.userRepository = userRepository;
        this.auditTrail = auditTrail;
        this.currentUserProvider = currentUserProvider;
        this.identityOutboxPublisher = identityOutboxPublisher;
    }

    /**
     * Provisions an identity and its local projection.
     *
     * <p>This is the privileged administrative path, not a registration form. No password is
     * accepted or generated: the account is created requiring a credential reset, and the initial
     * credential reaches the person through the university's own onboarding process.
     *
     * <p>Ordinary students should not arrive this way at all — their identity originates in the
     * university's registration system. This exists for staff, and for the case where an
     * administrator must provision someone by hand.
     */
    @Transactional
    public UserResponse provision(ProvisionIdentityRequest request) {
        requireAvailable();
        UUID actor = actorId();

        if (userRepository.existsByUsername(request.username())) {
            throw new ResourceAlreadyExistsException(
                    IdentityErrorCode.USERNAME_ALREADY_EXISTS, "Username " + request.username() + " is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResourceAlreadyExistsException(
                    IdentityErrorCode.EMAIL_ALREADY_EXISTS, "Email " + request.email() + " is already registered");
        }

        IdentityAccount account = identityProvider.createAccount(new NewIdentityAccount(
                request.username(),
                request.email(),
                request.firstName(),
                request.lastName(),
                request.studentNumber(),
                request.realmRoles() == null ? Set.of() : Set.copyOf(request.realmRoles())));

        User local = userRepository.saveAndFlush(User.fromIdentityProvider(
                account.subject(), account.username(), account.email(), account.firstName(), account.lastName()));

        auditTrail.record(
                actor,
                AuditTrail.Action.IDENTITY_PROVISIONED,
                ENTITY,
                local.getId(),
                "Provisioned by an administrator; roles " + account.realmRoles());
        log.info("Provisioned identity {} for username {}", local.getId(), account.username());
        return UserResponse.from(local);
    }

    /**
     * Disables authentication for an account, and reflects it locally.
     *
     * <p>The provider call is what actually revokes access. The local status is a projection kept so
     * that reads do not need a round trip — it is never the control itself.
     */
    @Transactional
    public UserResponse disable(UUID userId) {
        return setEnabled(userId, false);
    }

    @Transactional
    public UserResponse enable(UUID userId) {
        return setEnabled(userId, true);
    }

    private UserResponse setEnabled(UUID userId, boolean enabled) {
        requireAvailable();
        User user = require(userId);
        String subject = requireLinked(user);

        identityProvider.setEnabled(subject, enabled);

        if (enabled) {
            user.activate();
        } else {
            user.suspend();
        }
        auditTrail.record(
                actorId(),
                enabled ? AuditTrail.Action.ACCOUNT_ENABLED : AuditTrail.Action.ACCOUNT_DISABLED,
                ENTITY,
                userId,
                enabled ? "Authentication re-enabled at the identity provider" : "Authentication revoked at the identity provider");
        log.info("{} account {} at the identity provider", enabled ? "Enabled" : "Disabled", userId);
        return UserResponse.from(user);
    }

    /**
     * Roles as the identity provider holds them — the only place they mean anything.
     *
     * <p>Authorities are derived from the token, so a role that is not in the provider grants
     * nothing no matter what any local table says.
     */
    @Transactional(readOnly = true)
    public List<String> rolesOf(UUID userId) {
        requireAvailable();
        return identityProvider.realmRoles(requireLinked(require(userId))).stream()
                .sorted()
                .toList();
    }

    @Transactional
    public List<String> grantRole(UUID userId, String role) {
        requireAvailable();
        String subject = requireLinked(require(userId));
        identityProvider.grantRealmRole(subject, role);
        auditTrail.record(actorId(), AuditTrail.Action.ROLE_GRANTED, ENTITY, userId, "Granted realm role " + role);
        identityOutboxPublisher.publishRoleGranted(userId, role);
        log.info("Granted realm role {} to user {}", role, userId);
        return rolesOf(userId);
    }

    @Transactional
    public List<String> revokeRole(UUID userId, String role) {
        requireAvailable();
        String subject = requireLinked(require(userId));
        identityProvider.revokeRealmRole(subject, role);
        auditTrail.record(actorId(), AuditTrail.Action.ROLE_REVOKED, ENTITY, userId, "Revoked realm role " + role);
        log.info("Revoked realm role {} from user {}", role, userId);
        return rolesOf(userId);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void requireAvailable() {
        if (!identityProvider.isAvailable()) {
            throw new BusinessException(
                    IdentityErrorCode.IDENTITY_PROVIDER_UNAVAILABLE,
                    "Identity-provider administration is not configured in this environment");
        }
    }

    private User require(UUID userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        IdentityErrorCode.USER_NOT_FOUND, "No user exists with id " + userId));
    }

    /**
     * A local row with no subject predates the identity provider and cannot be administered through
     * it. Refusing is the honest answer: there is no account to disable.
     */
    private String requireLinked(User user) {
        String subject = user.getKeycloakSubject();
        if (subject == null || subject.isBlank()) {
            throw new BusinessException(
                    IdentityErrorCode.IDENTITY_NOT_LINKED,
                    "This account is not linked to an identity provider and cannot be administered");
        }
        return subject;
    }

    /** Null when the operation runs outside a request; the audit row then records no actor. */
    private UUID actorId() {
        return currentUserProvider.find().map(CurrentUser::userId).orElse(null);
    }
}
