package com.university.lms.identity.integration.keycloak;

import com.university.lms.identity.spi.IdentityAccount;
import com.university.lms.identity.spi.NewIdentityAccount;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Translates between Keycloak's user representation and this application's vocabulary.
 *
 * <p>Isolated so that the provider's JSON shape — which changes between Keycloak versions — is
 * described in exactly one file.
 */
final class KeycloakUserMapper {

    /** The attribute the realm's user profile declares as administratively owned. */
    static final String STUDENT_NUMBER_ATTRIBUTE = "student_number";

    private KeycloakUserMapper() {}

    static IdentityAccount toAccount(Map<?, ?> representation, Set<String> realmRoles) {
        return new IdentityAccount(
                text(representation, "id"),
                text(representation, "username"),
                text(representation, "email"),
                text(representation, "firstName"),
                text(representation, "lastName"),
                representation.get("enabled") instanceof Boolean enabled && enabled,
                studentNumber(representation),
                realmRoles);
    }

    static Map<String, Object> toRepresentation(NewIdentityAccount request) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("username", request.username());
        user.put("email", request.email());
        user.put("firstName", request.firstName());
        user.put("lastName", request.lastName());
        // Created enabled but unusable: the required action forces a credential to be set before
        // the account can authenticate. No password is sent, because none is known.
        user.put("enabled", true);
        user.put("emailVerified", false);
        user.put("requiredActions", List.of("UPDATE_PASSWORD"));
        if (request.studentNumber() != null && !request.studentNumber().isBlank()) {
            user.put("attributes", Map.of(STUDENT_NUMBER_ATTRIBUTE, List.of(request.studentNumber())));
        }
        return user;
    }

    private static Optional<String> studentNumber(Map<?, ?> representation) {
        if (!(representation.get("attributes") instanceof Map<?, ?> attributes)) {
            return Optional.empty();
        }
        if (!(attributes.get(STUDENT_NUMBER_ATTRIBUTE) instanceof List<?> values) || values.isEmpty()) {
            return Optional.empty();
        }
        return values.get(0) instanceof String value && !value.isBlank() ? Optional.of(value) : Optional.empty();
    }

    private static String text(Map<?, ?> representation, String key) {
        return representation.get(key) instanceof String value ? value : null;
    }
}
