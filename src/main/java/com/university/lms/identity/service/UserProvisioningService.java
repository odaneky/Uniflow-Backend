package com.university.lms.identity.service;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.security.TokenClaimReader;
import com.university.lms.identity.domain.IdentityErrorCode;
import com.university.lms.identity.domain.User;
import com.university.lms.identity.repository.UserRepository;
import com.university.lms.identity.spi.StudentNumberDirectory;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Resolves a validated token to the local user row that represents that person, creating or linking
 * one on first sight.
 *
 * <p>This is <b>authentication provisioning</b>, and it is not the same thing as academic
 * provisioning. It creates the local projection of an identity Keycloak already authenticated; it
 * never creates a {@code Student}. A person who logs in with no academic record is a legitimate
 * state — they are authenticated and have nothing to see — and it is the university's provisioning
 * process, not a login, that decides whether they are a student.
 *
 * <h2>Correlation order, and why it is this order</h2>
 *
 * <ol>
 *   <li><b>Keycloak subject.</b> Immutable and issued by the identity provider. Always tried first.
 *   <li><b>Institutional student number.</b> The correlation key the university actually owns, and
 *       the one that connects a login to a record the registry created in advance.
 *   <li><b>Verified email.</b> Last resort, and only when the provider asserts it is verified.
 * </ol>
 *
 * <p>Email is deliberately last. It is the weakest of the three: an unverified address is a claim
 * the caller made about themselves, and honouring it would let anyone able to register in the realm
 * with someone else's address inherit that person's academic record.
 *
 * <h2>Linking is one-way and one-time</h2>
 *
 * <p>An account may be linked to a subject exactly once. If correlation finds a local user already
 * linked to a <em>different</em> subject, the request is refused and recorded — it is never
 * relinked. Silent relinking is the single most dangerous behaviour this class could have: it would
 * turn any ability to influence a student number or email attribute in the identity provider into a
 * complete takeover of that student's academic record, transcript included.
 *
 * <p><b>Why this is a separate bean.</b> The write must run in its own transaction: ownership
 * checks are made from inside {@code readOnly = true} service methods, and an insert attempted
 * there fails. {@code REQUIRES_NEW} only takes effect through the proxy, so calling it from a
 * sibling method of the same class would silently do nothing.
 */
@Service
public class UserProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

    private static final String ENTITY = "User";

    private final UserRepository userRepository;
    private final TokenClaimReader claims;
    private final AuditTrail auditTrail;

    /**
     * Optional: a deployment without the student module correlates on subject and email alone.
     * Resolved lazily rather than injected directly so that absence is a supported configuration
     * rather than a start-up failure.
     */
    private final ObjectProvider<StudentNumberDirectory> studentNumbers;

    private final UserProvisioningTransactions transactions;

    public UserProvisioningService(
            UserRepository userRepository,
            TokenClaimReader claims,
            AuditTrail auditTrail,
            ObjectProvider<StudentNumberDirectory> studentNumbers,
            UserProvisioningTransactions transactions) {
        this.userRepository = userRepository;
        this.claims = claims;
        this.auditTrail = auditTrail;
        this.studentNumbers = studentNumbers;
        this.transactions = transactions;
    }

    /**
     * Not itself transactional: each step below opens its own, so that a failed insert can be
     * recovered from by reading in a transaction that is not already poisoned.
     */
    public User provision(Jwt jwt) {
        String subject = claims.subject(jwt)
                .orElseThrow(() -> new BusinessException(
                        IdentityErrorCode.IDENTITY_NOT_LINKED,
                        "The access token carries no subject and cannot be resolved to a user"));

        Optional<User> byStudentNumber = correlateByStudentNumber(jwt);
        if (byStudentNumber.isPresent()) {
            return link(byStudentNumber.get(), subject, "student number");
        }

        Optional<User> byEmail = correlateByVerifiedEmail(jwt);
        if (byEmail.isPresent()) {
            return link(byEmail.get(), subject, "verified email");
        }

        return create(jwt, subject);
    }

    // ------------------------------------------------------------------
    // Correlation
    // ------------------------------------------------------------------

    private Optional<User> correlateByStudentNumber(Jwt jwt) {
        StudentNumberDirectory directory = studentNumbers.getIfAvailable();
        if (directory == null) {
            return Optional.empty();
        }
        return claims.studentNumber(jwt)
                .map(String::trim)
                .filter(number -> !number.isEmpty())
                // Matched exactly as the university asserts it. A case-insensitive match would be
                // ambiguous, because the uniqueness constraint on student_number is case-sensitive
                // and could therefore hold two numbers that differ only in case.
                .flatMap(directory::findUserIdByStudentNumber)
                .flatMap(userRepository::findById);
    }

    private Optional<User> correlateByVerifiedEmail(Jwt jwt) {
        if (!claims.emailVerified(jwt)) {
            return Optional.empty();
        }
        return claims.email(jwt).flatMap(userRepository::findByEmailIgnoreCase);
    }

    // ------------------------------------------------------------------
    // Linking and creation
    // ------------------------------------------------------------------

    private User link(User existing, String subject, String matchedOn) {
        if (subject.equals(existing.getKeycloakSubject())) {
            return existing;
        }
        if (existing.getKeycloakSubject() != null) {
            // A different identity is claiming an account that is already linked. Treat as an
            // attempted takeover: refuse, and make sure the attempt outlives the failed request.
            auditTrail.record(
                    null,
                    AuditTrail.Action.IDENTITY_LINK_REFUSED,
                    ENTITY,
                    existing.getId(),
                    "Subject " + subject + " matched on " + matchedOn
                            + " but the account is already linked to a different subject");
            log.error(
                    "SECURITY: refusing to relink user {} (matched on {}) — already linked to another subject",
                    existing.getId(),
                    matchedOn);
            throw new BusinessException(
                    IdentityErrorCode.IDENTITY_CONFLICT,
                    "This account is already linked to a different identity-provider user");
        }

        existing.linkToIdentityProvider(subject);
        User linked = transactions.save(existing);
        auditTrail.record(
                linked.getId(),
                AuditTrail.Action.IDENTITY_LINKED,
                ENTITY,
                linked.getId(),
                "Linked to identity-provider subject, matched on " + matchedOn);
        log.info("Linked existing user {} to an identity-provider subject, matched on {}", linked.getId(), matchedOn);
        return linked;
    }

    private User create(Jwt jwt, String subject) {
        try {
            User created = transactions.save(User.fromIdentityProvider(
                    subject,
                    uniqueUsername(jwt, subject),
                    availableEmail(jwt, subject),
                    claims.givenName(jwt).orElse("Unknown"),
                    claims.familyName(jwt).orElse("User")));

            auditTrail.record(
                    created.getId(),
                    AuditTrail.Action.IDENTITY_PROVISIONED,
                    ENTITY,
                    created.getId(),
                    "Provisioned on first authentication; no academic record is implied");
            log.info("Provisioned user {} from an identity-provider subject", created.getId());
            return created;
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent first requests from the same person. The unique index is the real
            // guarantee; this is only how the loser of the race learns the answer. The read runs in
            // a fresh transaction because the one that just violated the constraint is aborted and
            // will refuse every subsequent statement.
            return transactions.findBySubject(subject).orElseThrow(() -> ex);
        }
    }

    /**
     * An address this row can actually be created with.
     *
     * <p>The token's email may already belong to an account we are not permitted to link to —
     * typically because the provider has not verified it. Refusing the login would be the wrong
     * answer: the identity provider has authenticated this person, and locking them out of the
     * portal over a duplicate contact address punishes them for an administrative collision.
     *
     * <p>So the account is created under a placeholder, and the collision is audited for someone to
     * reconcile. The {@code .invalid} TLD is reserved by RFC 2606 and can never be routable, so the
     * placeholder cannot silently start receiving mail.
     */
    private String availableEmail(Jwt jwt, String subject) {
        String asserted = claims.email(jwt).orElse(null);
        if (asserted == null || userRepository.existsByEmailIgnoreCase(asserted)) {
            if (asserted != null) {
                auditTrail.record(
                        null,
                        AuditTrail.Action.IDENTITY_SYNC_FAILED,
                        ENTITY,
                        null,
                        "Asserted email already belongs to another account; provisioned with a placeholder");
                log.warn("Email asserted by the identity provider is already in use; using a placeholder");
            }
            return placeholderEmail(subject);
        }
        return asserted;
    }

    /**
     * Usernames are unique here but need not stay unique over time in the identity provider, and a
     * local row may already hold the name. A suffix is preferable to failing the request: the
     * username is a label, while the subject is the identity.
     */
    private String uniqueUsername(Jwt jwt, String subject) {
        String preferred = claims.username(jwt).orElse(subject).toLowerCase(Locale.ROOT);
        if (!userRepository.existsByUsername(preferred)) {
            return preferred;
        }
        String suffixed = preferred + "-" + subject.substring(0, Math.min(8, subject.length()));
        return userRepository.existsByUsername(suffixed) ? preferred + "-" + UUID.randomUUID() : suffixed;
    }

    /**
     * Email is {@code NOT NULL} and unique, and a provider is not obliged to assert one. The
     * {@code .invalid} TLD is reserved by RFC 2606 and can never be routable, so this can never
     * become an address that receives mail by accident.
     */
    private static String placeholderEmail(String subject) {
        return subject + "@no-email.invalid";
    }
}
