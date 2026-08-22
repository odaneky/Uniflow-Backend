package com.university.lms.request.api;

import com.university.lms.request.domain.ServiceRequestStatus;
import com.university.lms.request.domain.ServiceRequestType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Read-only service request facts for outbox handlers and other modules. */
public interface RequestDirectory {

    record RequestSummary(
            UUID id,
            UUID studentId,
            UUID studentUserId,
            ServiceRequestType type,
            ServiceRequestStatus status,
            String reference,
            UUID assignedTo,
            UUID deliverableDocumentId,
            Instant updatedAt) {}

    Optional<RequestSummary> findById(UUID requestId);

    Optional<UUID> studentUserIdOf(UUID studentId);
}
