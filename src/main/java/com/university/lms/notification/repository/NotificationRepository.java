package com.university.lms.notification.repository;

import com.university.lms.notification.domain.Notification;
import com.university.lms.notification.domain.NotificationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the notification module. */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndStatus(UUID recipientUserId, NotificationStatus status);

    /** Paged rather than unbounded: the dispatcher drains the backlog in batches. */
    List<Notification> findByStatus(NotificationStatus status, Pageable pageable);
}
