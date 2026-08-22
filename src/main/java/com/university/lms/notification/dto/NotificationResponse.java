package com.university.lms.notification.dto;

import com.university.lms.notification.domain.Notification;
import com.university.lms.notification.domain.NotificationStatus;
import com.university.lms.notification.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;

/** A notification as its recipient may see it. Carries no delivery-provider detail. */
public record NotificationResponse(
        UUID id,
        NotificationType type,
        NotificationStatus status,
        String title,
        String body,
        Instant sentAt,
        String actionUrl,
        String sourceType,
        UUID sourceId) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getNotificationType(),
                notification.getStatus(),
                notification.getTitle(),
                notification.getBody(),
                notification.getSentAt(),
                notification.getActionUrl(),
                notification.getSourceType(),
                notification.getSourceId());
    }
}
