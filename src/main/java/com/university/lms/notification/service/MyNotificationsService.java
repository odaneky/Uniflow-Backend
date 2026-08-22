package com.university.lms.notification.service;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.notification.dto.NotificationResponse;
import com.university.lms.notification.repository.NotificationRepository;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The caller's own notifications.
 *
 * <p>Addressed by user rather than by student, so staff receive theirs on the same path. The
 * recipient is always the authenticated principal — a notification is a message to a person, and
 * there is no legitimate reason for one caller to read another's.
 */
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
                notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipient, pageable),
                NotificationResponse::from);
    }
}
