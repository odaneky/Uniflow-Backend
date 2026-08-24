package com.university.lms.notification.service;

import com.university.lms.notification.api.Notifier;
import com.university.lms.notification.domain.Notification;
import com.university.lms.notification.domain.NotificationChannel;
import com.university.lms.notification.domain.NotificationType;
import com.university.lms.notification.repository.NotificationRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes system-initiated notifications.
 *
 * <p>{@code REQUIRES_NEW} and failure-tolerant on purpose. A notification is a message *about* a
 * change, not part of it: if the write fails, the exam has still moved and the caller should still
 * succeed. Letting it roll the caller back would mean a full notifications table could stop the
 * examinations office rescheduling anything.
 */
@Service
public class DefaultNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultNotifier.class);

    private final NotificationRepository notificationRepository;

    public DefaultNotifier(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyUser(UUID recipientUserId, NotificationType type, String title, String body, String actionUrl) {
        if (recipientUserId == null) {
            return;
        }
        try {
            Notification notification =
                    new Notification(recipientUserId, type, NotificationChannel.IN_APP, title, body);
            if (actionUrl != null && !actionUrl.isBlank()) {
                notification.assignActionUrl(actionUrl);
            }
            notificationRepository.save(notification);
        } catch (RuntimeException ex) {
            log.error("Could not notify user {} about {}", recipientUserId, type, ex);
        }
    }
}
