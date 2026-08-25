package com.university.lms.student.domain;

import com.university.lms.common.exception.ErrorCode;

/** Error vocabulary owned by the student module. */
public enum StudentErrorCode implements ErrorCode {

    STUDENT_NOT_FOUND,
    STUDENT_NUMBER_ALREADY_EXISTS,
    STUDENT_ALREADY_EXISTS_FOR_USER,
    STUDENT_USER_NOT_FOUND,
    STUDENT_PROGRAMME_NOT_FOUND,
    INVALID_STUDENT_STATE,
    /** A status change must say why, so the audit trail records more than "this changed". */
    STUDENT_STATUS_REASON_REQUIRED,
    PROGRAMME_MEMBERSHIP_NOT_FOUND,
    PROGRAMME_MEMBERSHIP_ALREADY_OPEN,
    /** The primary membership can only be ended by transferring to a new one. */
    PROGRAMME_MEMBERSHIP_IS_PRIMARY,
    ADVISING_APPOINTMENT_NOT_FOUND;

    @Override
    public String code() {
        return name();
    }
}
