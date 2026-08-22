package com.university.lms.notification.repository;

import com.university.lms.notification.domain.NotificationChannel;
import com.university.lms.notification.domain.NotificationPreference;
import com.university.lms.notification.domain.NotificationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    List<NotificationPreference> findByUserId(UUID userId);

    Optional<NotificationPreference> findByUserIdAndNotificationTypeAndChannel(
            UUID userId, NotificationType notificationType, NotificationChannel channel);
}
