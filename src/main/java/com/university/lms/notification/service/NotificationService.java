package com.university.lms.notification.service;

import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.notification.domain.Notification;
import com.university.lms.notification.domain.NotificationChannel;
import com.university.lms.notification.domain.NotificationType;
import com.university.lms.notification.dto.CreateNotificationRequest;
import com.university.lms.notification.dto.NotificationResponse;
import com.university.lms.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queues an in-app notification. Delivery to email/push is a later dispatcher; in-app is the row
 * itself, so it is marked sent in the same transaction that creates it.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserDirectory userDirectory;

    public NotificationService(
            NotificationRepository notificationRepository,
            CurrentUserProvider currentUserProvider,
            UserDirectory userDirectory) {
        this.notificationRepository = notificationRepository;
        this.currentUserProvider = currentUserProvider;
        this.userDirectory = userDirectory;
    }

    @Transactional
    public NotificationResponse create(CreateNotificationRequest request) {
        requireStaff();
        if (!userDirectory.exists(request.recipientUserId())) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
        Notification notification = new Notification(
                request.recipientUserId(),
                request.notificationType(),
                NotificationChannel.IN_APP,
                request.title(),
                request.body());
        if (request.actionUrl() != null && !request.actionUrl().isBlank()) {
            notification.assignActionUrl(request.actionUrl().trim());
        }
        notification.markSent(Instant.now());
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public void delete(UUID notificationId) {
        requireStaff();
        Notification notification = notificationRepository
                .findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND, "No notification exists with id " + notificationId));
        notificationRepository.delete(notification);
    }

    /** Removes outdated demo rows so seed can replace them with course-labelled copy. */
    @Transactional
    public int deleteByRecipientAndType(UUID recipientUserId, NotificationType type) {
        requireStaff();
        var rows = notificationRepository.findByRecipientUserIdAndNotificationType(recipientUserId, type);
        notificationRepository.deleteAll(rows);
        return rows.size();
    }

    private void requireStaff() {
        if (!currentUserProvider.require().isStaff()) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
    }
}
