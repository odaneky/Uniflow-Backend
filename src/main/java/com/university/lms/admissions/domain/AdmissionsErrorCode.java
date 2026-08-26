package com.university.lms.admissions.domain;

import com.university.lms.common.exception.ErrorCode;

public enum AdmissionsErrorCode implements ErrorCode {
    APPLICATION_NOT_FOUND,
    /** Caller is neither admissions staff nor the holder of this application's capability token. */
    APPLICATION_ACCESS_DENIED,
    APPLICATION_ALREADY_OPEN,
    APPLICATION_ALREADY_CLOSED,
    APPLICATION_INVALID_TRANSITION,
    APPLICATION_INVALID_DECISION,
    APPLICATION_NOT_EDITABLE,
    APPLICATION_DEPOSIT_REQUIRED,
    APPLICATION_DEPOSIT_ALREADY_RECORDED,
    APPLICATION_ALREADY_MATRICULATED,
    APPLICATION_DOCUMENT_NOT_FOUND,
    APPLICATION_PROGRAMME_NOT_FOUND,
    APPLICATION_TERM_NOT_FOUND,
    APPLICATION_MATRICULATION_FAILED,
    APPLICATION_FORM_INVALID,
    APPLICATION_FORM_FIELD_MISSING,
    APPLICATION_DOCUMENT_ALREADY_DECIDED;

    @Override
    public String code() {
        return name();
    }
}
