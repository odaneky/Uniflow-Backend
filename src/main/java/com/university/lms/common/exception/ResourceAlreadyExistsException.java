package com.university.lms.common.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/** Creating the resource would violate a uniqueness rule. */
public class ResourceAlreadyExistsException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceAlreadyExistsException(ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.CONFLICT, message);
    }

    public ResourceAlreadyExistsException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, HttpStatus.CONFLICT, message, cause);
    }
}
