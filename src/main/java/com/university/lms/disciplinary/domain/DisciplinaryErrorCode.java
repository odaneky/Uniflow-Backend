package com.university.lms.disciplinary.domain;

import com.university.lms.common.exception.ErrorCode;

/** Error vocabulary owned by the disciplinary module. */
public enum DisciplinaryErrorCode implements ErrorCode {

    CASE_NOT_FOUND,
    INVALID_CASE_TRANSITION,
    /** Resolving or dismissing a case must say why and what came of it. */
    CASE_OUTCOME_REQUIRED;

    @Override
    public String code() {
        return name();
    }
}
