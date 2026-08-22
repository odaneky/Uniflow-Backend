package com.university.lms.communication.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummaryResponse(
        UUID id,
        String subject,
        UUID courseSectionId,
        Instant updatedAt,
        String lastMessagePreview,
        UUID lastSenderUserId,
        String lastSenderName) {}
