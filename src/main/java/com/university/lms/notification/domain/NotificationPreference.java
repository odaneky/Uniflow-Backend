package com.university.lms.notification.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(
        name = "notification_preferences",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_notification_preferences_user_type_channel",
                        columnNames = {"user_id", "notification_type", "channel"}),
        indexes = @Index(name = "idx_notification_preferences_user", columnList = "user_id"))
@Getter
public class NotificationPreference extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    protected NotificationPreference() {}

    public NotificationPreference(
            UUID userId, NotificationType notificationType, NotificationChannel channel, boolean enabled) {
        this.userId = userId;
        this.notificationType = notificationType;
        this.channel = channel;
        this.enabled = enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
