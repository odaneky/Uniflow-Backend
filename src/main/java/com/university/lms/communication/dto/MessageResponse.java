package com.university.lms.communication.dto;

import com.university.lms.communication.domain.Message;
import java.time.Instant;
import java.util.UUID;

public record MessageResponse(UUID id, UUID senderUserId, String senderName, String body, Instant sentAt) {

    public static MessageResponse from(Message message, String senderName) {
        return new MessageResponse(
                message.getId(),
                message.getSenderUserId(),
                senderName,
                message.visibleBody(),
                message.getSentAt());
    }
}
