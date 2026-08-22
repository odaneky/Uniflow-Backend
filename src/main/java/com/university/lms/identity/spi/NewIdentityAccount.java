package com.university.lms.identity.spi;

import java.util.Set;

/**
 * A request to create an account at the identity provider.
 *
 * <p><b>There is no password field, and there must never be one.</b> The account is created
 * requiring a credential reset, and the initial credential is delivered by the university's own
 * onboarding process. UniFlow never learns, transports, or stores a password — which is the whole
 * point of delegating authentication.
 */
public record NewIdentityAccount(
        String username, String email, String firstName, String lastName, String studentNumber, Set<String> realmRoles) {}
