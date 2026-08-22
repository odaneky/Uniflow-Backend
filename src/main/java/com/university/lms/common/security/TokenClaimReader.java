package com.university.lms.common.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Reads identity out of a validated token, according to {@link ClaimMappingProperties}.
 *
 * <p>The single place in the application that knows what a claim is called. Everything else asks
 * for "the caller's email" rather than for {@code jwt.getClaim("email")}.
 *
 * <p>Every accessor is defensive. Claims are attacker-influenced data even after the signature has
 * been verified — the signature proves the identity provider issued the token, not that a claim has
 * the type or shape this code expects. A surprising value must yield {@link Optional#empty()}
 * rather than a {@code ClassCastException}, because an exception here would surface as a 500 on an
 * authentication path and turn a malformed token into a denial-of-service lever.
 */
@Component
public class TokenClaimReader {

    private final ClaimMappingProperties claims;

    public TokenClaimReader(ClaimMappingProperties claims) {
        this.claims = claims;
    }

    /** The immutable external identity. Absent only for a token that should never have validated. */
    public Optional<String> subject(Jwt jwt) {
        return text(jwt, claims.subject());
    }

    public Optional<String> username(Jwt jwt) {
        return text(jwt, claims.username());
    }

    public Optional<String> email(Jwt jwt) {
        return text(jwt, claims.email());
    }

    public Optional<String> givenName(Jwt jwt) {
        return text(jwt, claims.givenName());
    }

    public Optional<String> familyName(Jwt jwt) {
        return text(jwt, claims.familyName());
    }

    /**
     * The institutional student number asserted by the identity provider.
     *
     * <p>Trusted because it is inside a signed token from the configured issuer — which means the
     * realm must not let a user edit this attribute themselves. That is a Keycloak configuration
     * requirement, documented in {@code docker/keycloak/README.md}, and the correlation code treats
     * a mismatch as a security event rather than assuming this can never be wrong.
     */
    public Optional<String> studentNumber(Jwt jwt) {
        return text(jwt, claims.studentNumber());
    }

    /**
     * Only {@code true} counts. An absent or non-boolean claim is treated as unverified, so a
     * provider that omits it can never accidentally authorise an email-based account link.
     */
    public boolean emailVerified(Jwt jwt) {
        return jwt.getClaim(claims.emailVerified()) instanceof Boolean verified && verified;
    }

    /** Role names, with no {@code ROLE_} prefix applied — that is the converter's job. */
    public List<String> roles(Jwt jwt) {
        Object cursor = jwt.getClaims();
        for (String segment : claims.rolesPath()) {
            if (!(cursor instanceof Map<?, ?> map)) {
                return List.of();
            }
            cursor = map.get(segment);
        }
        if (!(cursor instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(role -> !role.isBlank())
                .toList();
    }

    private static Optional<String> text(Jwt jwt, String claim) {
        Object value = jwt.getClaim(claim);
        return value instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
    }
}
