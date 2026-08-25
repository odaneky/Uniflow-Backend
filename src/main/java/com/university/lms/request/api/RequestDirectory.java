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

    /**
     * The realm role that should be notified in addition to REGISTRAR when a request of this type
     * is submitted — e.g. FINANCIAL_AID_OFFICER for SAP_APPEAL, whose review capability is granted
     * in {@code ServiceRequestWorkflow}. Empty when REGISTRAR is the only intended reviewer.
     *
     * <p>Kept behind this api, rather than switched on {@link ServiceRequestType} by callers
     * outside this module, so the module-boundary rule stays satisfied without freezing a new
     * violation every time a type gains an additional notified role.
     */
    Optional<String> additionalNotificationRole(ServiceRequestType type);
}
