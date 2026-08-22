package com.university.lms.identity.spi;

import java.util.Optional;
import java.util.Set;

/**
 * An account as the identity provider sees it.
 *
 * <p>Deliberately carries no credential material of any kind — not a password, not a hash, not a
 * temporary secret, not a token. Nothing in UniFlow has any use for one, and a field that exists
 * will eventually be populated, logged, or serialised.
 *
 * @param subject the immutable external identity — the OIDC {@code sub}
 * @param enabled whether the provider will authenticate this account; distinct from any academic
 *     status UniFlow keeps, which answers a different question
 */
public record IdentityAccount(
        String subject,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        Optional<String> studentNumber,
        Set<String> realmRoles) {}
