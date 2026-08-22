package com.university.lms.common.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/** The caller is not authenticated. */
public class UnauthorizedException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.UNAUTHORIZED, message);
    }
}
