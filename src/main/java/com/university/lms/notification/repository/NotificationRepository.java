package com.university.lms.notification.repository;

import com.university.lms.notification.domain.Notification;
import com.university.lms.notification.domain.NotificationStatus;
import com.university.lms.notification.domain.NotificationType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Internal to the notification module. */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Unread first, then newest within each group. */
    @Query(
            """
            SELECT n FROM Notification n
            WHERE n.recipientUserId = :recipientUserId
            ORDER BY CASE WHEN n.readAt IS NULL THEN 0 ELSE 1 END, n.createdAt DESC
            """)
    Page<Notification> findForRecipientOrdered(@Param("recipientUserId") UUID recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndStatus(UUID recipientUserId, NotificationStatus status);

    /** Paged rather than unbounded: the dispatcher drains the backlog in batches. */
    List<Notification> findByStatus(NotificationStatus status, Pageable pageable);

    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

    List<Notification> findByRecipientUserIdAndNotificationType(UUID recipientUserId, NotificationType notificationType);
}
