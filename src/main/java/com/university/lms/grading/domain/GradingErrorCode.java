package com.university.lms.grading.domain;

import com.university.lms.common.exception.ErrorCode;

/** Error vocabulary owned by the grading module. */
public enum GradingErrorCode implements ErrorCode {
    GRADE_NOT_FOUND,
    GRADE_SCALE_NOT_FOUND,
    GRADE_BAND_NOT_FOUND,
    GRADE_SECTION_NOT_FOUND,
    GRADE_STUDENT_NOT_FOUND;

    @Override
    public String code() {
        return name();
    }
}
