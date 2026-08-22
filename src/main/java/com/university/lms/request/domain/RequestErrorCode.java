package com.university.lms.request.domain;

import com.university.lms.common.exception.ErrorCode;

public enum RequestErrorCode implements ErrorCode {
    REQUEST_NOT_FOUND,
    REQUEST_ALREADY_OPEN,
    REQUEST_ALREADY_DECIDED,
    REQUEST_INVALID_DECISION;

    @Override
    public String code() {
        return name();
    }
}
