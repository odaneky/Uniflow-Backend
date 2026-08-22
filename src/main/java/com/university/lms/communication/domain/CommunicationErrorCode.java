package com.university.lms.communication.domain;

import com.university.lms.common.exception.ErrorCode;

public enum CommunicationErrorCode implements ErrorCode {
    ANNOUNCEMENT_NOT_FOUND,
    CONVERSATION_NOT_FOUND,
    CONVERSATION_PARTICIPANT_NOT_FOUND;

    @Override
    public String code() {
        return name();
    }
}
