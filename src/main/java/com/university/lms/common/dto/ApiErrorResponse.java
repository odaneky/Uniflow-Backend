package com.university.lms.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * The single error shape returned by every endpoint in the system.
 *
 * <p>Clients branch on {@link #code()}; {@link #message()} is for humans and may change without
 * notice. {@link #traceId()} correlates the response with server logs.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId,
        List<ApiFieldError> errors) {

    public static ApiErrorResponse of(int status, String code, String message, String path, String traceId) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, traceId, List.of());
    }

    public static ApiErrorResponse of(
            int status, String code, String message, String path, String traceId, List<ApiFieldError> errors) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, traceId, errors);
    }
}
