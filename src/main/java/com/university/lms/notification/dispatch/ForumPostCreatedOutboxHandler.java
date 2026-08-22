package com.university.lms.notification.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.outbox.DomainOutbox;
import com.university.lms.common.outbox.OutboxEventHandler;
import com.university.lms.notification.domain.Notification;
import com.university.lms.notification.domain.NotificationChannel;
import com.university.lms.notification.domain.NotificationType;
import com.university.lms.notification.service.NotificationDeliveryService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ForumPostCreatedOutboxHandler implements OutboxEventHandler {

    public static final String EVENT_TYPE = "ForumPostCreated";

    private final ObjectMapper objectMapper;
    private final NotificationDeliveryService notificationDeliveryService;

    public ForumPostCreatedOutboxHandler(
            ObjectMapper objectMapper, NotificationDeliveryService notificationDeliveryService) {
        this.objectMapper = objectMapper;
        this.notificationDeliveryService = notificationDeliveryService;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(DomainOutbox row) throws Exception {
        JsonNode payload = objectMapper.readTree(row.getPayload());
        UUID postId = UUID.fromString(payload.get("postId").asText());
        UUID topicId = UUID.fromString(payload.get("topicId").asText());
        UUID topicAuthorUserId = UUID.fromString(payload.get("topicAuthorUserId").asText());
        String topicTitle = payload.get("topicTitle").asText();
        String senderName = payload.has("senderName") ? payload.get("senderName").asText() : "Someone";
        String courseCode = payload.has("courseCode") ? payload.get("courseCode").asText().toLowerCase() : null;

        String title = "New reply in \"" + truncate(topicTitle, 80) + "\"";
        String body = senderName + " replied to your discussion thread.";
        String actionUrl = courseCode != null
                ? "courses/" + courseCode + "/discussions/" + topicId
                : "notifications";

        Notification notification = new Notification(
                topicAuthorUserId,
                NotificationType.FORUM_REPLY,
                NotificationChannel.IN_APP,
                title,
                body);
        notification.assignSource("FORUM_POST", postId);
        notification.assignActionUrl(actionUrl);
        notification.markSent(Instant.now());
        notificationDeliveryService.deliverInApp(notification);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
