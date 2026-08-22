package com.university.lms.communication.domain;

import com.university.lms.common.exception.ErrorCode;

public enum CommunicationErrorCode implements ErrorCode {
    ANNOUNCEMENT_NOT_FOUND,
    CONVERSATION_NOT_FOUND,
    CONVERSATION_PARTICIPANT_NOT_FOUND,
    MESSAGE_NOT_FOUND,
    RATE_LIMIT_EXCEEDED,
    MESSAGE_ATTACHMENT_NOT_FOUND,
    FORUM_TOPIC_NOT_FOUND,
    FORUM_POST_NOT_FOUND,
    FORUM_TOPIC_LOCKED;

    @Override
    public String code() {
        return name();
    }
}
