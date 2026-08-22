package com.university.lms.curriculum.domain;

import com.university.lms.common.exception.ErrorCode;

public enum CurriculumErrorCode implements ErrorCode {
    REQUIREMENT_BLOCK_NOT_FOUND,
    REQUIREMENT_BLOCK_NAME_EXISTS,
    REQUIREMENT_COURSE_UNKNOWN,
    REQUIREMENT_COURSE_ALREADY_LISTED,
    PROGRAMME_NOT_FOUND;

    @Override
    public String code() {
        return name();
    }
}
