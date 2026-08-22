package com.university.lms.common.exception;

import java.io.Serial;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type for every deliberately-thrown application error.
 *
 * <p>Carries the two things the API error contract needs that a plain exception does not: a stable
 * {@link ErrorCode} and the HTTP status the failure maps to. Holding the status here (rather than
 * inferring it in the handler) lets a module say that its particular business rule is a 409 rather
 * than the 422 its parent type would default to.
 */
@Getter
public abstract class ApplicationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient ErrorCode errorCode;
    private final HttpStatus status;

    protected ApplicationException(ErrorCode errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    protected ApplicationException(ErrorCode errorCode, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
    }

}
