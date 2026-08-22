package com.university.lms.admissions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

/** Append-only status transition log for an application. */
@Entity
@Table(
        name = "application_events",
        indexes = @Index(name = "idx_application_events_application", columnList = "application_id,created_at"))
@Getter
public class ApplicationEvent {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private ApplicationStatus toStatus;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "note", length = 2000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ApplicationEvent() {
        // for JPA
    }

    public ApplicationEvent(
            UUID applicationId,
            ApplicationStatus fromStatus,
            ApplicationStatus toStatus,
            UUID actorUserId,
            String note,
            Instant at) {
        this.applicationId = applicationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorUserId = actorUserId;
        this.note = note;
        this.createdAt = at;
    }
}
