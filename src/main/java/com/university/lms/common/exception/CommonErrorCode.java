package com.university.lms.common.exception;

/** Cross-cutting error codes that are not owned by any single business module. */
public enum CommonErrorCode implements ErrorCode {

    VALIDATION_ERROR,
    MALFORMED_REQUEST,
    CONSTRAINT_VIOLATION,
    RESOURCE_NOT_FOUND,
    RESOURCE_ALREADY_EXISTS,
    DATA_INTEGRITY_VIOLATION,
    CONCURRENT_MODIFICATION,
    ACCESS_DENIED,
    RATE_LIMIT_EXCEEDED,
    AUTHENTICATION_REQUIRED,
    METHOD_NOT_ALLOWED,
    UNSUPPORTED_MEDIA_TYPE,
    INTERNAL_ERROR;

    @Override
    public String code() {
        return name();
    }
}
