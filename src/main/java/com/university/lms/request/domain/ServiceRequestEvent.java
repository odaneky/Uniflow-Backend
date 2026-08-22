package com.university.lms.request.domain;

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

/** Append-only status transition log for a service request. */
@Entity
@Table(
        name = "service_request_events",
        indexes = @Index(name = "idx_service_request_events_request", columnList = "request_id,created_at"))
@Getter
public class ServiceRequestEvent {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private ServiceRequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private ServiceRequestStatus toStatus;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "note", length = 2000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ServiceRequestEvent() {
        // for JPA
    }

    public ServiceRequestEvent(
            UUID requestId,
            ServiceRequestStatus fromStatus,
            ServiceRequestStatus toStatus,
            UUID actorUserId,
            String note,
            Instant at) {
        this.requestId = requestId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorUserId = actorUserId;
        this.note = note;
        this.createdAt = at;
    }
}
