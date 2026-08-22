package com.university.lms.common.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * Validation that could only be performed in the service layer failed — as opposed to bean
 * validation at the API boundary, which is reported by the framework and translated separately.
 */
public class ValidationException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ValidationException(ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.BAD_REQUEST, message);
    }
}
