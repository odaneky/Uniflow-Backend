package com.university.lms.course.domain;

import com.university.lms.common.exception.ErrorCode;

/** Error vocabulary owned by the course module. */
public enum CourseErrorCode implements ErrorCode {

    COURSE_NOT_FOUND,
    COURSE_CODE_ALREADY_EXISTS,
    COURSE_DEPARTMENT_NOT_FOUND,
    COURSE_NOT_OFFERABLE,
    COURSE_SECTION_NOT_FOUND,
    COURSE_SECTION_ALREADY_EXISTS,
    COURSE_SECTION_TERM_NOT_FOUND,
    INVALID_COURSE_STATE,
    REQUIREMENT_COURSE_UNKNOWN,
    INVALID_REQUIREMENT_GROUP,
    INVALID_MEETING,
    /** A lecturer or a room is already committed elsewhere at the same time in this term. */
    SCHEDULE_CONFLICT,
    INVALID_SECTION_STATE,
    COURSE_SECTION_IN_USE;

    @Override
    public String code() {
        return name();
    }
}
