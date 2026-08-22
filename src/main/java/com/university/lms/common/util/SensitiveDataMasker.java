package com.university.lms.common.util;

import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a rejected field value is safe to echo back in an error response.
 *
 * <p>Validation errors are far more useful when they show what was actually rejected, but a naive
 * implementation happily reflects a password or a bearer token straight back into a response body
 * and the access log. Fields whose name suggests a secret are reported without their value.
 */
public final class SensitiveDataMasker {

    private static final Set<String> SENSITIVE_FRAGMENTS =
            Set.of("password", "secret", "token", "credential", "authorization", "apikey", "pin", "otp");

    private SensitiveDataMasker() {}

    public static boolean isSensitive(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String normalised = fieldName.toLowerCase(Locale.ROOT).replace("_", "");
        return SENSITIVE_FRAGMENTS.stream().anyMatch(normalised::contains);
    }

    /** Returns the value only when the field name is not secret-shaped; otherwise {@code null}. */
    public static Object maskIfSensitive(String fieldName, Object value) {
        return isSensitive(fieldName) ? null : value;
    }
}
