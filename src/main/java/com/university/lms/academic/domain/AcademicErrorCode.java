package com.university.lms.academic.domain;

import com.university.lms.common.exception.ErrorCode;

/** Error vocabulary owned by the academic module. */
public enum AcademicErrorCode implements ErrorCode {

    FACULTY_NOT_FOUND,
    FACULTY_CODE_ALREADY_EXISTS,
    DEPARTMENT_NOT_FOUND,
    DEPARTMENT_CODE_ALREADY_EXISTS,
    PROGRAMME_NOT_FOUND,
    PROGRAMME_CODE_ALREADY_EXISTS,
    ACADEMIC_YEAR_NOT_FOUND,
    ACADEMIC_YEAR_CODE_ALREADY_EXISTS,
    ACADEMIC_TERM_NOT_FOUND,
    ACADEMIC_TERM_SEQUENCE_ALREADY_EXISTS,
    INVALID_DATE_RANGE,
    INVALID_CREDIT_LOAD,
    DEAN_NOT_FOUND,
    DEPARTMENT_HEAD_NOT_FOUND;

    @Override
    public String code() {
        return name();
    }
}
