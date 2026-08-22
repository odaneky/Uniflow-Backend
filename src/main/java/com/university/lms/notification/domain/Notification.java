package com.university.lms.notification.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * A message queued for delivery to one recipient.
 *
 * <p>Persisted first and delivered afterwards. Sending from inside the transaction that caused the
 * notification would put a network call on the critical path of a database commit — the request
 * would block on the mail server, and a delivery failure would roll back the enrolment that
 * triggered it. The row is the durable record; a dispatcher moves it out of {@link
 * NotificationStatus#PENDING}.
 */
@Entity
@Table(
        name = "notifications",
        indexes = {
            @Index(name = "idx_notifications_recipient", columnList = "recipient_user_id"),
            @Index(name = "idx_notifications_status", columnList = "status")
        })
@Getter
public class Notification extends BaseEntity {

    /** Cross-module reference into identity. */
    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    protected Notification() {
        // for JPA
    }

    public Notification(
            UUID recipientUserId,
            NotificationType notificationType,
            NotificationChannel channel,
            String title,
            String body) {
        this.recipientUserId = recipientUserId;
        this.notificationType = notificationType;
        this.channel = channel;
        this.title = title;
        this.body = body;
    }

    public void markSent(Instant at) {
        this.status = NotificationStatus.SENT;
        this.sentAt = at;
    }

    public void markFailed(String reason) {
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason;
    }

    public void markRead(Instant at) {
        this.status = NotificationStatus.READ;
        this.readAt = at;
    }
}
