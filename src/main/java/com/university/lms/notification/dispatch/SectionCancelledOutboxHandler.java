package com.university.lms.notification.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.outbox.DomainOutbox;
import com.university.lms.common.outbox.OutboxEventHandler;
import com.university.lms.enrollment.service.EnrollmentOutboxPublisher;
import com.university.lms.notification.domain.Notification;
import com.university.lms.notification.domain.NotificationChannel;
import com.university.lms.notification.domain.NotificationType;
import com.university.lms.notification.service.NotificationDeliveryService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Notifies a student when their section is cancelled by the institution. */
@Component
public class SectionCancelledOutboxHandler implements OutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final NotificationDeliveryService notificationDeliveryService;

    public SectionCancelledOutboxHandler(
            ObjectMapper objectMapper, NotificationDeliveryService notificationDeliveryService) {
        this.objectMapper = objectMapper;
        this.notificationDeliveryService = notificationDeliveryService;
    }

    @Override
    public String eventType() {
        return EnrollmentOutboxPublisher.EVENT_SECTION_CANCELLED;
    }

    @Override
    public void handle(DomainOutbox row) throws Exception {
        JsonNode payload = objectMapper.readTree(row.getPayload());
        UUID enrollmentId = UUID.fromString(payload.get("enrollmentId").asText());
        UUID studentUserId = UUID.fromString(payload.get("studentUserId").asText());
        String courseCode = textOrNull(payload, "courseCode");
        String courseTitle = textOrNull(payload, "courseTitle");

        String subject = courseTitle != null && !courseTitle.isBlank()
                ? (courseCode != null ? courseCode + " · " + courseTitle : courseTitle)
                : courseCode != null ? courseCode : "your course";
        String title = "Section cancelled";
        String body = "Your enrolment in " + subject + " has been cancelled by the registry. Any charges for it"
                + " have been reversed.";

        Notification notification =
                new Notification(studentUserId, NotificationType.ENROLMENT, NotificationChannel.IN_APP, title, body);
        notification.assignSource("ENROLLMENT", enrollmentId);
        notification.assignActionUrl("/enrollments");
        notification.markSent(Instant.now());
        notificationDeliveryService.deliverInApp(notification);
    }

    private static String textOrNull(JsonNode payload, String field) {
        return payload.hasNonNull(field) ? payload.get(field).asText() : null;
    }
}
