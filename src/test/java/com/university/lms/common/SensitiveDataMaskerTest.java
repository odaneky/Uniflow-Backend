package com.university.lms.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.common.util.SensitiveDataMasker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Validation errors echo the rejected value back to the caller, which is helpful right up until
 * the rejected value is a password. These cases pin that behaviour down.
 */
class SensitiveDataMaskerTest {

    @ParameterizedTest
    @ValueSource(strings = {"password", "passwordHash", "password_hash", "currentPassword", "secret",
            "clientSecret", "token", "refreshToken", "apiKey", "api_key", "credential", "Authorization", "pin", "otp"})
    @DisplayName("secret-shaped field names are never echoed")
    void masksSecretShapedNames(String fieldName) {
        assertThat(SensitiveDataMasker.isSensitive(fieldName)).isTrue();
        assertThat(SensitiveDataMasker.maskIfSensitive(fieldName, "hunter2")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "studentNumber", "firstName", "credits", "courseCode"})
    @DisplayName("ordinary field values are preserved, because they make the error actionable")
    void preservesOrdinaryValues(String fieldName) {
        assertThat(SensitiveDataMasker.isSensitive(fieldName)).isFalse();
        assertThat(SensitiveDataMasker.maskIfSensitive(fieldName, "value")).isEqualTo("value");
    }

    @Test
    void toleratesNullFieldName() {
        assertThat(SensitiveDataMasker.isSensitive(null)).isFalse();
    }
}
