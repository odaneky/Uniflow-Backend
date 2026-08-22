package com.university.lms.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

    private static final String AUDIENCE = "university-lms-api";

    private final AudienceValidator validator = new AudienceValidator(AUDIENCE);

    private static Jwt jwtForAudience(List<String> audience) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("a-subject");
        if (audience != null) {
            builder.audience(audience);
        }
        return builder.build();
    }

    @Test
    @DisplayName("a token minted for this API is accepted")
    void acceptsMatchingAudience() {
        assertThat(validator.validate(jwtForAudience(List.of(AUDIENCE))).hasErrors())
                .isFalse();
    }

    @Test
    @DisplayName("a token carrying several audiences is accepted when ours is among them")
    void acceptsWhenPresentAlongsideOthers() {
        assertThat(validator
                        .validate(jwtForAudience(List.of("some-other-api", AUDIENCE)))
                        .hasErrors())
                .isFalse();
    }

    /**
     * The reason this validator exists. Every client in the realm shares an issuer, so a token
     * obtained for a different application would otherwise be a valid credential here.
     */
    @Test
    @DisplayName("a token from the same issuer but for a different client is rejected")
    void rejectsAnotherClientsToken() {
        assertThat(validator.validate(jwtForAudience(List.of("some-other-api"))).hasErrors())
                .isTrue();
    }

    @Test
    @DisplayName("a token with no audience at all is rejected")
    void rejectsMissingAudience() {
        assertThat(validator.validate(jwtForAudience(null)).hasErrors()).isTrue();
        assertThat(validator.validate(jwtForAudience(List.of())).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("an unconfigured audience fails fast rather than degrading to accept-anything")
    void refusesToBeConstructedWithoutAnAudience() {
        assertThatThrownBy(() -> new AudienceValidator(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AudienceValidator("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
