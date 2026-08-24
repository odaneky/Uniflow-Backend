package com.university.lms.grading.domain;

import com.university.lms.common.exception.ErrorCode;

/** Error vocabulary owned by the grading module. */
public enum GradingErrorCode implements ErrorCode {
    GRADE_NOT_FOUND,
    GRADE_SCALE_NOT_FOUND,
    GRADE_BAND_NOT_FOUND,
    GRADE_SECTION_NOT_FOUND,
    GRADE_STUDENT_NOT_FOUND,
    /** The grade is locked (term close) and this change did not come through the review workflow. */
    GRADE_LOCKED,
    /** A change to an already-awarded grade must say why; only the first award may omit it. */
    GRADE_REVISION_REASON_REQUIRED;

    @Override
    public String code() {
        return name();
    }
}
