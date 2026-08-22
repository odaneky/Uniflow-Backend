package com.university.lms.assessment.domain;

import com.university.lms.common.exception.ErrorCode;

/** Error vocabulary owned by the assessment module. */
public enum AssessmentErrorCode implements ErrorCode {
    ASSESSMENT_NOT_FOUND,
    ASSESSMENT_SECTION_NOT_FOUND,
    ATTEMPT_NOT_FOUND,
    ATTEMPT_NOT_FILE_BASED,
    ATTEMPT_FILE_REQUIRED,
    ATTEMPT_FILE_TOO_LARGE,
    ATTEMPT_FILE_TYPE_NOT_ALLOWED,
    ASSESSMENT_EXAM_BLOCKED,
    QUIZ_NOT_QUIZ_TYPE,
    QUIZ_QUESTION_NOT_FOUND,
    QUIZ_OPTION_NOT_FOUND,
    QUIZ_STRUCTURE_LOCKED,
    QUIZ_INVALID_QUESTION,
    QUIZ_ATTEMPT_NOT_IN_PROGRESS,
    QUIZ_ANSWER_INVALID,
    QUIZ_GRADE_NOT_MANUAL;

    @Override
    public String code() {
        return name();
    }
}
