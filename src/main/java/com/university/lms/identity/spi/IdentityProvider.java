package com.university.lms.identity.spi;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

/**
 * Administrative operations against the identity provider.
 *
 * <p>An <b>outbound port</b>. Everything the rest of UniFlow needs from Keycloak is expressed here
 * in the application's own vocabulary; no other package may know that Keycloak has a REST API, what
 * shape its representations take, or that it exists at all. Federating the realm to Active
 * Directory, or replacing Keycloak outright, is then a new adapter rather than a search across the
 * codebase.
 *
 * <p>This port covers <em>identity</em> only. It never carries academic data: programme, faculty,
 * grades and standing are UniFlow's, and pushing them into the provider's user attributes would
 * make it a second, unauthoritative academic database.
 */
public interface IdentityProvider {

    /**
     * Whether administrative operations are configured in this environment.
     *
     * <p>Reading tokens works without this — validation needs only the JWKS. Administration needs
     * service-account credentials, which a developer machine or test run legitimately may not have.
     * Callers should check rather than discover it through an exception.
     */
    boolean isAvailable();

    Optional<IdentityAccount> findBySubject(String subject);

    Optional<IdentityAccount> findByUsername(String username);

    /** Creates a disabled-until-credentialled account; see {@link NewIdentityAccount}. */
    IdentityAccount createAccount(NewIdentityAccount request);

    /**
     * Enables or disables authentication for an account.
     *
     * <p>Disabling here is what actually stops someone signing in. A status flag in UniFlow's own
     * database does not, and must never be presented as though it does.
     */
    void setEnabled(String subject, boolean enabled);

    Set<String> realmRoles(String subject);

    void grantRealmRole(String subject, String role);

    void revokeRealmRole(String subject, String role);

    /**
     * Where to send a user who wants to change their password or manage their credentials.
     *
     * <p>UniFlow offers a link, never a form. It has no password to change.
     */
    URI accountManagementUri();

    /**
     * Keycloak subjects that currently hold this realm role. Empty when none do.
     *
     * <p>Used to list teaching staff for assignment pickers. It is a directory read, not a
     * security control — callers must still authorise who may assign a lecturer.
     */
    java.util.List<String> subjectsWithRealmRole(String role);
}
