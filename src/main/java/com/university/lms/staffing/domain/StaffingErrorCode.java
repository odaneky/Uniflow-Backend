package com.university.lms.staffing.domain;

import com.university.lms.common.exception.ErrorCode;

public enum StaffingErrorCode implements ErrorCode {
    ORG_UNIT_NOT_FOUND,
    ORG_UNIT_CODE_EXISTS,
    ORG_UNIT_PARENT_NOT_FOUND,
    STAFF_APPOINTMENT_NOT_FOUND,
    STAFF_APPOINTMENT_USER_NOT_FOUND,
    EMPLOYEE_ALREADY_REGISTERED,
    EMPLOYEE_USER_NOT_FOUND;

    @Override
    public String code() {
        return name();
    }
}
