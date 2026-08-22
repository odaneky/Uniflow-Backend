package com.university.lms.identity.integration.keycloak;

import com.university.lms.common.exception.BusinessException;
import com.university.lms.identity.domain.IdentityErrorCode;
import com.university.lms.identity.spi.IdentityAccount;
import com.university.lms.identity.spi.IdentityProvider;
import com.university.lms.identity.spi.NewIdentityAccount;
import java.net.URI;
import java.util.Optional;
import java.util.Set;

/**
 * Stands in when administrative access to the identity provider is not configured.
 *
 * <p>Every operation fails loudly with a stable error code. This is the important part: the
 * alternative — a no-op implementation that returns success — would let an administrator disable an
 * account, see a green response, and believe access had been revoked. A security control that
 * reports success without acting is worse than one that is plainly absent, so this one is plainly
 * absent.
 *
 * <p>Token <em>validation</em> is unaffected and needs nothing from here; the application still
 * authenticates and authorises normally.
 */
class UnavailableIdentityProvider implements IdentityProvider {

    private static final String MESSAGE =
            "Identity-provider administration is not configured in this environment";

    private final String accountUri;

    UnavailableIdentityProvider(String accountUri) {
        this.accountUri = accountUri;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Optional<IdentityAccount> findBySubject(String subject) {
        throw unavailable();
    }

    @Override
    public Optional<IdentityAccount> findByUsername(String username) {
        throw unavailable();
    }

    @Override
    public IdentityAccount createAccount(NewIdentityAccount request) {
        throw unavailable();
    }

    @Override
    public void setEnabled(String subject, boolean enabled) {
        throw unavailable();
    }

    @Override
    public Set<String> realmRoles(String subject) {
        throw unavailable();
    }

    @Override
    public void grantRealmRole(String subject, String role) {
        throw unavailable();
    }

    @Override
    public void revokeRealmRole(String subject, String role) {
        throw unavailable();
    }

    @Override
    public URI accountManagementUri() {
        // Safe to answer even unconfigured: it is derived from the issuer, not from credentials.
        return URI.create(accountUri);
    }

    @Override
    public java.util.List<String> subjectsWithRealmRole(String role) {
        return java.util.List.of();
    }

    private BusinessException unavailable() {
        return new BusinessException(IdentityErrorCode.IDENTITY_PROVIDER_UNAVAILABLE, MESSAGE);
    }
}
