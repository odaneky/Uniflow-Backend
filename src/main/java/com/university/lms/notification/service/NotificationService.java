package com.university.lms.notification.service;

import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.notification.domain.Notification;
import com.university.lms.notification.domain.NotificationChannel;
import com.university.lms.notification.dto.CreateNotificationRequest;
import com.university.lms.notification.dto.NotificationResponse;
import com.university.lms.notification.repository.NotificationRepository;
import java.time.Instant;
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
        if (!currentUserProvider.require().isStaff()) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
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
        notification.markSent(Instant.now());
        return NotificationResponse.from(notificationRepository.save(notification));
    }
}
