package com.university.lms.document.domain;

import com.university.lms.common.exception.ErrorCode;

public enum DocumentErrorCode implements ErrorCode {
    DOCUMENT_NOT_FOUND,
    DOCUMENT_STORAGE_KEY_EXISTS,
    DOCUMENT_STORE_FAILED,
    DOCUMENT_TOO_LARGE,
    DOCUMENT_CONTENT_TYPE_NOT_ALLOWED,
    DOCUMENT_INFECTED,
    DOCUMENT_SCAN_UNAVAILABLE;

    @Override
    public String code() {
        return name();
    }
}
