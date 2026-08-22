package com.university.lms.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One field-level validation failure.
 *
 * @param field         the offending property path, e.g. {@code email}
 * @param message       human-readable explanation
 * @param rejectedValue the value that was rejected; omitted entirely when absent or redacted
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiFieldError(String field, String message, Object rejectedValue) {

    public static ApiFieldError of(String field, String message) {
        return new ApiFieldError(field, message, null);
    }
}
