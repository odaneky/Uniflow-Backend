package com.university.lms.notification.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.outbox.DomainOutbox;
import com.university.lms.common.outbox.OutboxEventHandler;
import com.university.lms.communication.api.AnnouncementDirectory;
import com.university.lms.notification.domain.Notification;
import com.university.lms.notification.domain.NotificationChannel;
import com.university.lms.notification.domain.NotificationType;
import com.university.lms.notification.service.NotificationDeliveryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Creates durable IN_APP notifications when an announcement is published. */
@Component
public class AnnouncementPublishedOutboxHandler implements OutboxEventHandler {

    public static final String EVENT_TYPE = "AnnouncementPublished";

    private final ObjectMapper objectMapper;
    private final AnnouncementDirectory announcementDirectory;
    private final NotificationDeliveryService notificationDeliveryService;

    public AnnouncementPublishedOutboxHandler(
            ObjectMapper objectMapper,
            AnnouncementDirectory announcementDirectory,
            NotificationDeliveryService notificationDeliveryService) {
        this.objectMapper = objectMapper;
        this.announcementDirectory = announcementDirectory;
        this.notificationDeliveryService = notificationDeliveryService;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(DomainOutbox row) throws Exception {
        JsonNode payload = objectMapper.readTree(row.getPayload());
        UUID announcementId = UUID.fromString(payload.get("announcementId").asText());

        AnnouncementDirectory.AnnouncementSummary announcement = announcementDirectory
                .findPublishedById(announcementId)
                .orElseThrow(() -> new IllegalStateException("Published announcement not found: " + announcementId));

        List<UUID> recipients = announcementDirectory.recipientUserIds(announcement);
        String actionUrl = "/announcements/" + announcementId;
        Instant now = Instant.now();

        for (UUID recipientUserId : recipients) {
            Notification notification = new Notification(
                    recipientUserId,
                    NotificationType.ANNOUNCEMENT,
                    NotificationChannel.IN_APP,
                    announcement.title(),
                    announcement.bodyPreview());
            notification.assignSource("ANNOUNCEMENT", announcementId);
            notification.assignActionUrl(actionUrl);
            notification.markSent(now);
            notificationDeliveryService.deliverInApp(notification);
        }
    }
}
