package com.university.lms.common.security;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Requires that a token was minted for <em>this</em> API.
 *
 * <p>Issuer validation alone is not enough. Every client in the realm — including the browser-side
 * development client, and any client added later for an unrelated purpose — receives tokens from
 * the same issuer. Accepting on issuer alone means a token obtained for some other application in
 * the same realm is a valid credential here, which quietly turns any new Keycloak client into a
 * way in. Checking {@code aud} narrows acceptance to tokens actually intended for this API.
 */
public final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error ERROR = new OAuth2Error(
            "invalid_token",
            "The required audience is missing",
            "https://datatracker.ietf.org/doc/html/rfc9068#section-3");

    private final String requiredAudience;

    public AudienceValidator(String requiredAudience) {
        if (requiredAudience == null || requiredAudience.isBlank()) {
            // Refusing to start beats starting with the check disabled: a misconfigured audience
            // would otherwise degrade silently into "accept anything from this issuer".
            throw new IllegalArgumentException("A required audience must be configured");
        }
        this.requiredAudience = requiredAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audience = token.getAudience();
        if (audience != null && audience.contains(requiredAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(ERROR);
    }
}
