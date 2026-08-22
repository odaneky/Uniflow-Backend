package com.university.lms.notification.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.outbox.DomainOutbox;
import com.university.lms.common.outbox.OutboxEventHandler;
import com.university.lms.grading.service.GradeOutboxPublisher;
import com.university.lms.notification.domain.Notification;
import com.university.lms.notification.domain.NotificationChannel;
import com.university.lms.notification.domain.NotificationType;
import com.university.lms.notification.service.NotificationDeliveryService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Notifies a student when a grade is published. */
@Component
public class GradePublishedOutboxHandler implements OutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final NotificationDeliveryService notificationDeliveryService;

    public GradePublishedOutboxHandler(
            ObjectMapper objectMapper, NotificationDeliveryService notificationDeliveryService) {
        this.objectMapper = objectMapper;
        this.notificationDeliveryService = notificationDeliveryService;
    }

    @Override
    public String eventType() {
        return GradeOutboxPublisher.EVENT_PUBLISHED;
    }

    @Override
    public void handle(DomainOutbox row) throws Exception {
        JsonNode payload = objectMapper.readTree(row.getPayload());
        UUID gradeId = UUID.fromString(payload.get("gradeId").asText());
        UUID studentUserId = UUID.fromString(payload.get("studentUserId").asText());
        String courseCode = textOrNull(payload, "courseCode");
        String courseTitle = textOrNull(payload, "courseTitle");
        String actionUrl = textOrNull(payload, "actionUrl");
        if (actionUrl == null) {
            actionUrl = courseCode == null ? "/grades" : "/courses/" + courseCode + "/grades";
        }

        String title;
        if (courseCode != null && courseTitle != null) {
            title = courseCode + " · " + courseTitle;
        } else if (courseCode != null) {
            title = courseCode + " · Grade published";
        } else {
            title = "Grade published";
        }

        String body = courseTitle != null && !courseTitle.isBlank()
                ? "A new grade has been released for " + courseTitle + "."
                : courseCode != null
                        ? "A new grade has been released for " + courseCode + "."
                        : "A new grade has been released.";

        Notification notification = new Notification(
                studentUserId, NotificationType.GRADE_PUBLISHED, NotificationChannel.IN_APP, title, body);
        notification.assignSource("GRADE", gradeId);
        notification.assignActionUrl(actionUrl);
        notification.markSent(Instant.now());
        notificationDeliveryService.deliverInApp(notification);
    }

    private static String textOrNull(JsonNode payload, String field) {
        return payload.hasNonNull(field) ? payload.get(field).asText() : null;
    }
}
