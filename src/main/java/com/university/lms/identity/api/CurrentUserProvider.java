package com.university.lms.identity.api;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the authenticated caller to a domain identity.
 *
 * <p>The application's single security abstraction. Business services depend on this rather than on
 * {@code Jwt}, {@code JwtAuthenticationToken} or {@code SecurityContextHolder}, which keeps the
 * domain independent of the security framework and of the identity provider behind it.
 *
 * <p>Published from {@code identity} because that module owns users; every other module asks
 * through this interface rather than reading {@code users} itself.
 */
public interface CurrentUserProvider {

    /**
     * The caller, provisioning a local user row on first sight of an unknown external identity.
     *
     * @throws com.university.lms.common.exception.UnauthorizedException when no authenticated
     *     bearer token is present
     */
    CurrentUser require();

    /** Empty when the caller is unauthenticated. Never provisions, so it is safe to call anywhere. */
    Optional<CurrentUser> find();

    /**
     * The caller's roles, read straight from the token.
     *
     * <p>Needs no local user row and never provisions one, which matters: a member of staff acting
     * for the first time has no row yet, and a check based on {@link #find()} would treat them as
     * anonymous. Role is a property of the token; identity is a property of the database. Conflating
     * the two makes authorization depend on whether someone happens to have signed in before.
     */
    Set<String> callerRoles();

    /** True when the caller holds any role other than {@code STUDENT}. */
    boolean isStaffCaller();

    /**
     * Where to send someone who wants to change their password or manage their credentials.
     *
     * <p>UniFlow offers a link and never a form, because it has no password to change. Exposed here
     * so that a client can render an account-security action without knowing that Keycloak exists.
     */
    URI accountManagementUri();
}
