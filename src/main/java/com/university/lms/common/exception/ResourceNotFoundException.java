package com.university.lms.common.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/** The addressed resource does not exist (or is not visible to the caller). */
public class ResourceNotFoundException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.NOT_FOUND, message);
    }
}
