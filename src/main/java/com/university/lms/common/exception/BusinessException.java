package com.university.lms.common.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * A domain rule was violated by an otherwise well-formed request — the caller sent something
 * syntactically valid that the business will not accept (e.g. enrolling into a closed section).
 */
public class BusinessException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, HttpStatus.UNPROCESSABLE_ENTITY, message, cause);
    }

    public BusinessException(ErrorCode errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
