package com.university.lms.learning.domain;

import com.university.lms.common.exception.ErrorCode;

/** Error vocabulary owned by the learning module. */
public enum LearningErrorCode implements ErrorCode {
    LEARNING_SECTION_NOT_FOUND,
    LEARNING_CONTENT_NOT_FOUND,
    LEARNING_MODULE_NOT_FOUND,
    LEARNING_LESSON_NOT_FOUND;

    @Override
    public String code() {
        return name();
    }
}
