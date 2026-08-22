package com.university.lms.communication.dto;

import java.time.Instant;
import java.util.UUID;

/** Opaque cursor for message history: {@code sentAt|messageId}. */
public record MessageCursor(Instant sentAt, UUID id) {

    public String encode() {
        return sentAt.toString() + "|" + id;
    }

    public static MessageCursor decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int sep = raw.indexOf('|');
        if (sep <= 0) {
            throw new IllegalArgumentException("Invalid message cursor");
        }
        return new MessageCursor(Instant.parse(raw.substring(0, sep)), UUID.fromString(raw.substring(sep + 1)));
    }
}
