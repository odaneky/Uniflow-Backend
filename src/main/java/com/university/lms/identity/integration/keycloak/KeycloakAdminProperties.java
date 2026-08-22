package com.university.lms.identity.integration.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connection details for the identity provider's administrative API.
 *
 * <p>{@code clientSecret} must come from the environment or a secret store. It is never committed,
 * never logged, and never included in an error response — a leaked service-account secret is
 * equivalent to administrative access over every identity in the realm.
 *
 * @param enabled when false the application starts without administrative capability and refuses
 *     those operations explicitly. That is the correct posture for a test run or a developer who
 *     has no service-account credentials, and is much better than silently pretending to succeed.
 */
@ConfigurationProperties("lms.identity.keycloak")
public record KeycloakAdminProperties(
        @DefaultValue("false") boolean enabled,
        String baseUrl,
        String realm,
        String clientId,
        String clientSecret,
        @DefaultValue("5s") java.time.Duration connectTimeout,
        @DefaultValue("10s") java.time.Duration readTimeout) {

    public KeycloakAdminProperties {
        if (enabled) {
            require(baseUrl, "lms.identity.keycloak.base-url");
            require(realm, "lms.identity.keycloak.realm");
            require(clientId, "lms.identity.keycloak.client-id");
            require(clientSecret, "lms.identity.keycloak.client-secret");
            // Fail at start-up rather than on the first administrative call. A misconfiguration
            // discovered when an administrator tries to disable a compromised account is
            // discovered at the worst possible moment.
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required when lms.identity.keycloak.enabled is true");
        }
    }

    String adminRealmUri() {
        return baseUrl.replaceAll("/+$", "") + "/admin/realms/" + realm;
    }

    String tokenUri() {
        return baseUrl.replaceAll("/+$", "") + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    String accountUri() {
        return baseUrl.replaceAll("/+$", "") + "/realms/" + realm + "/account";
    }
}
