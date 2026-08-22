package com.university.lms.notification.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.outbox.DomainOutbox;
import com.university.lms.common.outbox.OutboxEventHandler;
import com.university.lms.communication.api.MessageDirectory;
import com.university.lms.notification.domain.Notification;
import com.university.lms.notification.domain.NotificationChannel;
import com.university.lms.notification.domain.NotificationType;
import com.university.lms.notification.service.NotificationDeliveryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Creates durable IN_APP notifications when a message is sent. */
@Component
public class MessageSentOutboxHandler implements OutboxEventHandler {

    public static final String EVENT_TYPE = "MessageSent";

    private final ObjectMapper objectMapper;
    private final MessageDirectory messageDirectory;
    private final NotificationDeliveryService notificationDeliveryService;

    public MessageSentOutboxHandler(
            ObjectMapper objectMapper,
            MessageDirectory messageDirectory,
            NotificationDeliveryService notificationDeliveryService) {
        this.objectMapper = objectMapper;
        this.messageDirectory = messageDirectory;
        this.notificationDeliveryService = notificationDeliveryService;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(DomainOutbox row) throws Exception {
        JsonNode payload = objectMapper.readTree(row.getPayload());
        UUID messageId = UUID.fromString(payload.get("messageId").asText());
        UUID conversationId = UUID.fromString(payload.get("conversationId").asText());
        UUID senderUserId = UUID.fromString(payload.get("senderUserId").asText());

        MessageDirectory.MessageSummary message = messageDirectory
                .findById(messageId)
                .orElseThrow(() -> new IllegalStateException("Message not found: " + messageId));

        String senderName = payload.has("senderName") ? payload.get("senderName").asText() : "Someone";
        String title = "New message from " + senderName;
        String body = message.bodyPreview();
        String actionUrl = "/messages/" + conversationId;

        List<UUID> recipients = messageDirectory.participantUserIds(conversationId).stream()
                .filter(userId -> !userId.equals(senderUserId))
                .toList();

        Instant now = Instant.now();
        for (UUID recipientUserId : recipients) {
            Notification notification = new Notification(
                    recipientUserId,
                    NotificationType.MESSAGE,
                    NotificationChannel.IN_APP,
                    title,
                    body);
            notification.assignSource("MESSAGE", messageId);
            notification.assignActionUrl(actionUrl);
            notification.markSent(now);
            notificationDeliveryService.deliverInApp(notification);
        }
    }
}
