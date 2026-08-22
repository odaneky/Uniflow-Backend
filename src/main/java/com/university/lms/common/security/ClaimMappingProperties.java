package com.university.lms.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where each piece of identity lives inside an access token.
 *
 * <p>Claim names are configuration, not constants. The defaults are Keycloak's, but the identity
 * provider is expected to change: a realm may federate to Active Directory, LDAP, Entra ID or
 * another university's IdP, and each can present the same facts under different names or nest roles
 * somewhere else entirely. Hard-coding {@code preferred_username} throughout the business code
 * turns any of those into a redeploy.
 *
 * <p>Nothing outside {@link TokenClaimReader} and the role converter should name a claim.
 *
 * @param subject immutable external identity identifier — the OIDC {@code sub}. Never the username.
 * @param username human-readable login name. For students this carries the institutional student ID.
 * @param studentNumber the institutional student number, when the IdP is configured to assert it.
 *     Kept separate from {@code username} on purpose: a username may be renamed, and correlation
 *     must not silently follow a rename onto a different person's academic record.
 * @param rolesPath path to the role list, walked segment by segment — Keycloak nests it at
 *     {@code realm_access.roles}, other providers put it at the top level.
 */
@ConfigurationProperties("lms.security.claims")
public record ClaimMappingProperties(
        @DefaultValue("sub") String subject,
        @DefaultValue("preferred_username") String username,
        @DefaultValue("email") String email,
        @DefaultValue("email_verified") String emailVerified,
        @DefaultValue("given_name") String givenName,
        @DefaultValue("family_name") String familyName,
        @DefaultValue("student_number") String studentNumber,
        @DefaultValue({"realm_access", "roles"}) List<String> rolesPath) {

    public ClaimMappingProperties {
        // A blank subject claim would silently resolve every caller to the same (null) identity,
        // which is the worst possible failure mode for an authorization system.
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("lms.security.claims.subject must not be blank");
        }
        if (rolesPath == null || rolesPath.isEmpty()) {
            throw new IllegalArgumentException("lms.security.claims.roles-path must not be empty");
        }
        rolesPath = List.copyOf(rolesPath);
    }
}
