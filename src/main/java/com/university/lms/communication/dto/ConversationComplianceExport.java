package com.university.lms.communication.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Compliance export of a message thread — includes message bodies for authorised review. */
public record ConversationComplianceExport(
        UUID conversationId,
        String subject,
        UUID courseSectionId,
        Instant exportedAt,
        List<ParticipantRow> participants,
        List<MessageRow> messages) {

    public record ParticipantRow(UUID userId, String displayName) {}

    public record MessageRow(
            UUID id,
            UUID senderUserId,
            String senderName,
            Instant sentAt,
            String body,
            UUID documentId,
            boolean deleted,
            Instant deletedAt) {}
}
