package com.university.lms.notification.service;

import com.university.lms.common.sse.SseEvent;
import com.university.lms.common.sse.SseEventPublisher;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.notification.api.EmailSender;
import com.university.lms.notification.domain.Notification;
import com.university.lms.notification.domain.NotificationChannel;
import com.university.lms.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists durable notifications and triggers live (SSE) and external (email) delivery.
 *
 * <p>Invoked from outbox handlers after the originating transaction has committed.
 */
@Service
public class NotificationDeliveryService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceService preferenceService;
    private final SseEventPublisher sseEventPublisher;
    private final EmailSender emailSender;
    private final UserDirectory userDirectory;
    private final boolean emailEnabled;

    public NotificationDeliveryService(
            NotificationRepository notificationRepository,
            NotificationPreferenceService preferenceService,
            SseEventPublisher sseEventPublisher,
            EmailSender emailSender,
            UserDirectory userDirectory,
            @Value("${lms.notifications.email.enabled:false}") boolean emailEnabled) {
        this.notificationRepository = notificationRepository;
        this.preferenceService = preferenceService;
        this.sseEventPublisher = sseEventPublisher;
        this.emailSender = emailSender;
        this.userDirectory = userDirectory;
        this.emailEnabled = emailEnabled;
    }

    @Transactional
    public void deliverInApp(Notification notification) {
        if (!preferenceService.isEnabled(
                notification.getRecipientUserId(), notification.getNotificationType(), NotificationChannel.IN_APP)) {
            return;
        }
        try {
            notificationRepository.save(notification);
        } catch (DataIntegrityViolationException ex) {
            return;
        }
        publishLive(notification);
        maybeSendEmail(notification);
    }

    private void publishLive(Notification notification) {
        Map<String, String> data = Map.of(
                "notificationId",
                notification.getId().toString(),
                "type",
                notification.getNotificationType().name(),
                "title",
                notification.getTitle(),
                "actionUrl",
                notification.getActionUrl() == null ? "" : notification.getActionUrl());
        sseEventPublisher.publish(
                notification.getRecipientUserId(),
                new SseEvent(notification.getId().toString(), "notification.created", data));
    }

    private void maybeSendEmail(Notification inAppNotification) {
        if (!emailEnabled) {
            return;
        }
        UUID recipientUserId = inAppNotification.getRecipientUserId();
        if (!preferenceService.isEnabled(
                recipientUserId, inAppNotification.getNotificationType(), NotificationChannel.EMAIL)) {
            return;
        }
        String email = userDirectory
                .findById(recipientUserId)
                .map(UserDirectory.UserSummary::email)
                .filter(value -> value != null && !value.isBlank())
                .orElse(null);
        if (email == null) {
            return;
        }

        Notification emailNotification = new Notification(
                recipientUserId,
                inAppNotification.getNotificationType(),
                NotificationChannel.EMAIL,
                inAppNotification.getTitle(),
                inAppNotification.getBody());
        if (inAppNotification.getSourceType() != null && inAppNotification.getSourceId() != null) {
            emailNotification.assignSource(inAppNotification.getSourceType(), inAppNotification.getSourceId());
        }
        if (inAppNotification.getActionUrl() != null) {
            emailNotification.assignActionUrl(inAppNotification.getActionUrl());
        }

        Instant now = Instant.now();
        try {
            notificationRepository.save(emailNotification);
            emailSender.send(new EmailSender.EmailMessage(email, inAppNotification.getTitle(), inAppNotification.getBody()));
            emailNotification.markSent(now);
        } catch (DataIntegrityViolationException ex) {
            return;
        } catch (Exception ex) {
            emailNotification.markFailed(truncate(ex.getMessage()));
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "Delivery failed";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
