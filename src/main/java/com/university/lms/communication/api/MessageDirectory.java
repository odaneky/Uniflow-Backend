package com.university.lms.communication.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only message lookups for other modules (notification dispatch, etc.). */
public interface MessageDirectory {

    record MessageSummary(
            UUID id,
            UUID conversationId,
            UUID senderUserId,
            String bodyPreview,
            String conversationSubject) {}

    Optional<MessageSummary> findById(UUID messageId);

    List<UUID> participantUserIds(UUID conversationId);
}
