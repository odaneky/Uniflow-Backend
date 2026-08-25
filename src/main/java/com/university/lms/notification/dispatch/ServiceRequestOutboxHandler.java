package com.university.lms.notification.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.outbox.DomainOutbox;
import com.university.lms.common.outbox.OutboxEventHandler;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.notification.domain.Notification;
import com.university.lms.notification.domain.NotificationChannel;
import com.university.lms.notification.domain.NotificationType;
import com.university.lms.notification.service.NotificationDeliveryService;
import com.university.lms.request.api.RequestDirectory;
import com.university.lms.request.domain.ServiceRequestStatus;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.request.service.ServiceRequestOutboxPublisher;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Notifies reviewers when a student files a request, and students on status changes. */
@Component
public class ServiceRequestOutboxHandler implements OutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final RequestDirectory requestDirectory;
    private final NotificationDeliveryService notificationDeliveryService;
    private final UserDirectory userDirectory;

    public ServiceRequestOutboxHandler(
            ObjectMapper objectMapper,
            RequestDirectory requestDirectory,
            NotificationDeliveryService notificationDeliveryService,
            UserDirectory userDirectory) {
        this.objectMapper = objectMapper;
        this.requestDirectory = requestDirectory;
        this.notificationDeliveryService = notificationDeliveryService;
        this.userDirectory = userDirectory;
    }

    @Override
    public String eventType() {
        return ServiceRequestOutboxPublisher.EVENT_SUBMITTED;
    }

    /**
     * Every submission reaches a reviewer, not just the ten request types that happen to end up
     * with a specific assignee. {@code assigned_to} used to be the only recipient this notified,
     * and it is null for every type but WITHDRAWAL — so ten of eleven request types notified
     * nobody on staff when a student filed one. REGISTRAR can review every type
     * ({@code ServiceRequestWorkflow.assertCanReview}), so it is always in the broadcast pool; the
     * specific assignee, when there is one, is notified in addition, deduplicated against it.
     *
     * <p>A6: some types additionally broadcast to another role — SAP_APPEAL to
     * FINANCIAL_AID_OFFICER, per {@link RequestDirectory#additionalNotificationRole}, since {@code
     * ServiceRequestWorkflow.assertCanReview} now grants that role review capability for that type;
     * without this, they would never see the submission to act on it. The decision of which type
     * maps to which role lives behind that api call, not a switch here, so this module never reaches
     * past {@code request.api} into {@code request.domain}.
     */
    @Override
    public void handle(DomainOutbox row) throws Exception {
        JsonNode payload = objectMapper.readTree(row.getPayload());
        UUID requestId = UUID.fromString(payload.get("requestId").asText());
        String reference = payload.get("reference").asText();
        ServiceRequestType type = ServiceRequestType.valueOf(payload.get("requestType").asText());
        RequestDirectory.RequestSummary request = requestDirectory
                .findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Request not found: " + requestId));

        Instant now = Instant.now();
        String title = "New " + type.displayName() + " request";
        String body = reference + " is waiting for your review";
        String actionUrl = "/admin/requests/" + requestId;

        Set<UUID> notified = new HashSet<>();
        if (request.assignedTo() != null) {
            deliver(request.assignedTo(), title, body, actionUrl, requestId, row.getId(), now);
            notified.add(request.assignedTo());
        }
        for (UserDirectory.UserSummary registrar : userDirectory.findByRealmRole(SecurityRoles.REGISTRAR)) {
            if (notified.add(registrar.id())) {
                deliver(registrar.id(), title, body, actionUrl, requestId, row.getId(), now);
            }
        }
        Optional<String> additionalRole = requestDirectory.additionalNotificationRole(type);
        if (additionalRole.isPresent()) {
            for (UserDirectory.UserSummary holder : userDirectory.findByRealmRole(additionalRole.get())) {
                if (notified.add(holder.id())) {
                    deliver(holder.id(), title, body, actionUrl, requestId, row.getId(), now);
                }
            }
        }
    }

    /** Handles status-changed and delivered events via dedicated beans. */
    static void deliverStudentUpdate(
            NotificationDeliveryService delivery,
            UUID studentUserId,
            String title,
            String body,
            UUID requestId,
            UUID sourceId,
            Instant now) {
        if (studentUserId == null) {
            return;
        }
        Notification notification = new Notification(
                studentUserId, NotificationType.SERVICE_REQUEST, NotificationChannel.IN_APP, title, body);
        notification.assignSource("SERVICE_REQUEST", sourceId);
        notification.assignActionUrl("/requests/" + requestId);
        notification.markSent(now);
        delivery.deliverInApp(notification);
    }

    private void deliver(
            UUID recipientUserId,
            String title,
            String body,
            String actionUrl,
            UUID requestId,
            UUID sourceId,
            Instant now) {
        Notification notification = new Notification(
                recipientUserId, NotificationType.SERVICE_REQUEST, NotificationChannel.IN_APP, title, body);
        notification.assignSource("SERVICE_REQUEST", sourceId);
        notification.assignActionUrl(actionUrl);
        notification.markSent(now);
        notificationDeliveryService.deliverInApp(notification);
    }
}

@Component
class ServiceRequestStatusChangedOutboxHandler implements OutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final RequestDirectory requestDirectory;
    private final NotificationDeliveryService notificationDeliveryService;

    ServiceRequestStatusChangedOutboxHandler(
            ObjectMapper objectMapper,
            RequestDirectory requestDirectory,
            NotificationDeliveryService notificationDeliveryService) {
        this.objectMapper = objectMapper;
        this.requestDirectory = requestDirectory;
        this.notificationDeliveryService = notificationDeliveryService;
    }

    @Override
    public String eventType() {
        return ServiceRequestOutboxPublisher.EVENT_STATUS_CHANGED;
    }

    @Override
    public void handle(DomainOutbox row) throws Exception {
        JsonNode payload = objectMapper.readTree(row.getPayload());
        UUID requestId = UUID.fromString(payload.get("requestId").asText());
        ServiceRequestStatus status = ServiceRequestStatus.valueOf(payload.get("newStatus").asText());
        String reference = payload.get("reference").asText();
        ServiceRequestType type = ServiceRequestType.valueOf(payload.get("requestType").asText());
        RequestDirectory.RequestSummary request = requestDirectory
                .findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Request not found: " + requestId));

        Instant now = Instant.now();
        String title = type.displayName() + " request updated";
        String body = reference + " is now " + status.name().replace('_', ' ').toLowerCase();
        ServiceRequestOutboxHandler.deliverStudentUpdate(
                notificationDeliveryService,
                request.studentUserId(),
                title,
                body,
                requestId,
                row.getId(),
                now);

        if (status == ServiceRequestStatus.SUBMITTED && request.assignedTo() != null) {
            Notification notification = new Notification(
                    request.assignedTo(),
                    NotificationType.SERVICE_REQUEST,
                    NotificationChannel.IN_APP,
                    title,
                    body);
            notification.assignSource("SERVICE_REQUEST", row.getId());
            notification.assignActionUrl("/admin/requests/" + requestId);
            notification.markSent(now);
            notificationDeliveryService.deliverInApp(notification);
        }
    }
}

@Component
class ServiceRequestDeliveredOutboxHandler implements OutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final RequestDirectory requestDirectory;
    private final NotificationDeliveryService notificationDeliveryService;

    ServiceRequestDeliveredOutboxHandler(
            ObjectMapper objectMapper,
            RequestDirectory requestDirectory,
            NotificationDeliveryService notificationDeliveryService) {
        this.objectMapper = objectMapper;
        this.requestDirectory = requestDirectory;
        this.notificationDeliveryService = notificationDeliveryService;
    }

    @Override
    public String eventType() {
        return ServiceRequestOutboxPublisher.EVENT_DELIVERED;
    }

    @Override
    public void handle(DomainOutbox row) throws Exception {
        JsonNode payload = objectMapper.readTree(row.getPayload());
        UUID requestId = UUID.fromString(payload.get("requestId").asText());
        String reference = payload.get("reference").asText();
        RequestDirectory.RequestSummary request = requestDirectory
                .findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Request not found: " + requestId));
        ServiceRequestOutboxHandler.deliverStudentUpdate(
                notificationDeliveryService,
                request.studentUserId(),
                "Your document is ready",
                reference + " has a deliverable ready to download",
                requestId,
                row.getId(),
                Instant.now());
    }
}
