package com.university.lms.admissions;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.admissions.access.ApplicationAccessToken;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApplicationAccessTokenTest {

    @Test
    @DisplayName("tokens are unique and long enough that guessing is not a threat model")
    void tokensAreUniqueAndLong() {
        Set<String> minted = new HashSet<>();
        IntStream.range(0, 10_000).forEach(i -> minted.add(ApplicationAccessToken.mint()));

        assertThat(minted).hasSize(10_000);
        // 32 bytes base64url without padding.
        assertThat(minted.iterator().next()).hasSize(43);
    }

    @Test
    @DisplayName("the stored form is a hash, never the token")
    void storesOnlyAHash() {
        String token = ApplicationAccessToken.mint();
        String hash = ApplicationAccessToken.hash(token);

        assertThat(hash).hasSize(64).doesNotContain(token);
        assertThat(ApplicationAccessToken.hash(token)).as("hashing is stable").isEqualTo(hash);
    }

    @Test
    @DisplayName("matches only the token it was derived from")
    void matchesOnlyItsOwnToken() {
        String token = ApplicationAccessToken.mint();
        String hash = ApplicationAccessToken.hash(token);

        assertThat(ApplicationAccessToken.matches(token, hash)).isTrue();
        assertThat(ApplicationAccessToken.matches(ApplicationAccessToken.mint(), hash)).isFalse();
        assertThat(ApplicationAccessToken.matches(token.substring(0, 42), hash)).isFalse();
    }

    /**
     * Rows created before tokens existed have a null hash. Nothing must treat that as "no check
     * required" — it has to refuse, or every legacy application becomes world-writable.
     */
    @Test
    @DisplayName("a null hash or null token never matches")
    void nullsNeverMatch() {
        assertThat(ApplicationAccessToken.matches(null, ApplicationAccessToken.hash("x"))).isFalse();
        assertThat(ApplicationAccessToken.matches("x", null)).isFalse();
        assertThat(ApplicationAccessToken.matches(null, null)).isFalse();
    }
}
