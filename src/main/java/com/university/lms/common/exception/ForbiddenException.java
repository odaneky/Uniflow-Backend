package com.university.lms.common.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/** The caller is authenticated but not permitted to perform this action. */
public class ForbiddenException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.FORBIDDEN, message);
    }
}
