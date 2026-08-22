package com.university.lms.common.outbox;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Durable async work queue row — written in the same transaction as the originating write. */
@Entity
@Table(
        name = "domain_outbox",
        indexes = @Index(name = "idx_domain_outbox_claimable", columnList = "next_attempt_at,created_at"))
@Getter
public class DomainOutbox extends BaseEntity {

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    protected DomainOutbox() {
        // for JPA
    }

    public DomainOutbox(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            String idempotencyKey) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.idempotencyKey = idempotencyKey;
    }

    public void markProcessing(String instanceId, Instant at) {
        this.status = OutboxStatus.PROCESSING;
        this.lockedAt = at;
        this.lockedBy = instanceId;
    }

    public void markProcessed(Instant at) {
        this.status = OutboxStatus.PROCESSED;
        this.processedAt = at;
        this.lockedAt = null;
        this.lockedBy = null;
        this.lastError = null;
    }

    public void markFailed(String error, Instant nextAttempt) {
        this.status = OutboxStatus.FAILED;
        this.attemptCount++;
        this.lastError = error;
        this.nextAttemptAt = nextAttempt;
        this.lockedAt = null;
        this.lockedBy = null;
    }
}
