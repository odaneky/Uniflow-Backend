package com.university.lms.admissions.access;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The applicant's capability token: mint, hash, compare.
 *
 * <p>A <b>capability</b>, not an authentication credential. It grants access to exactly one
 * application and says nothing about who is holding it — which is correct, because an applicant has
 * no university identity yet. That is the whole point of applying, and it is why this cannot be a
 * Keycloak account: UniFlow must not become a place where people who are not students get accounts.
 * Once someone is admitted and matriculates they receive a real identity, and this stops mattering.
 *
 * <p>Because it is a capability, it must never be treated as proof of identity, and it must never be
 * accepted for anything other than the single application it belongs to.
 */
public final class ApplicationAccessToken {

    /**
     * 256 bits from a CSPRNG. Enough that guessing is not a threat model, which is what lets the
     * stored form be a fast digest rather than a slow password hash.
     */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private ApplicationAccessToken() {}

    /** A fresh token. Returned to the applicant once and never recoverable afterwards. */
    public static String mint() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        // URL-safe without padding: it travels in a link before it settles into a header.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** What gets persisted. A stolen backup yields hashes, not usable tokens. */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the platform; absence means a broken JRE, not a runtime case.
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    /**
     * Constant-time comparison.
     *
     * <p>{@code String.equals} returns as soon as two characters differ, and that timing difference
     * is measurable across a network given enough samples — it leaks the hash prefix by prefix.
     * Irrelevant to guessing a 256-bit token, but this is the kind of detail that gets copied into
     * somewhere it does matter, so it is done correctly here.
     */
    public static boolean matches(String presented, String storedHash) {
        if (presented == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(presented).getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8));
    }
}
