package com.university.lms.notification.service;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.notification.domain.Notification;
import com.university.lms.notification.dto.NotificationResponse;
import com.university.lms.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MyNotificationsService {

    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;

    public MyNotificationsService(
            NotificationRepository notificationRepository, CurrentUserProvider currentUserProvider) {
        this.notificationRepository = notificationRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public PageResponse<NotificationResponse> own(Pageable pageable) {
        UUID recipient = currentUserProvider.require().userId();
        return PageResponse.from(
                notificationRepository.findForRecipientOrdered(recipient, pageable), NotificationResponse::from);
    }

    public long unreadCount() {
        UUID recipient = currentUserProvider.require().userId();
        return notificationRepository.countByRecipientUserIdAndReadAtIsNull(recipient);
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        UUID recipient = currentUserProvider.require().userId();
        Notification notification = notificationRepository
                .findById(notificationId)
                .filter(n -> n.getRecipientUserId().equals(recipient))
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.ACCESS_DENIED, "No notification exists with id " + notificationId));
        notification.markRead(Instant.now());
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllRead() {
        UUID recipient = currentUserProvider.require().userId();
        Instant now = Instant.now();
        notificationRepository
                .findForRecipientOrdered(recipient, org.springframework.data.domain.Pageable.unpaged())
                .forEach(n -> {
                    if (n.getReadAt() == null) {
                        n.markRead(now);
                    }
                });
    }
}
