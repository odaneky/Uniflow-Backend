package com.university.lms.administration.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An immutable record that something consequential happened — a student was enrolled, a grade was
 * changed, a role was granted.
 *
 * <p>Distinct from the {@code created_by}/{@code updated_by} columns on {@link BaseEntity}: those
 * say who last touched a row and are overwritten by the next write. This is an append-only
 * history, which is what an academic appeal or an audit actually needs. Rows are never updated or
 * deleted, so there is no {@code @Version} and no mutator.
 */
@Entity
@Table(
        name = "audit_events",
        indexes = {
            @Index(name = "idx_audit_events_occurred", columnList = "occurred_at"),
            @Index(name = "idx_audit_events_entity", columnList = "entity_type,entity_id"),
            @Index(name = "idx_audit_events_actor", columnList = "actor_user_id")
        })
@Getter
public class AuditEvent extends BaseEntity {

    /** Cross-module reference into identity; null for actions taken by the system itself. */
    @Column(name = "actor_user_id")
    private UUID actorUserId;

    /**
     * Display name of the actor at the time of the event. Snapshotted so the trail stays readable
     * without joining users, and after the account is renamed or removed.
     */
    @Column(name = "actor_label", length = 200)
    private String actorLabel;

    /** Stable verb, e.g. {@code GRADE_CHANGED}. */
    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    /** Free-form JSON detail. Must never carry credentials or unnecessary personal data. */
    @Column(name = "details", length = 4000)
    private String details;

    /** Why, in the actor's own words — mandatory on a correction, optional elsewhere. */
    @Column(name = "reason", length = 1000)
    private String reason;

    /** Pre-serialized JSON snapshot of the affected fields before the change. Null on first creation. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value", columnDefinition = "jsonb")
    private String beforeValue;

    /** Pre-serialized JSON snapshot of the affected fields after the change. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value", columnDefinition = "jsonb")
    private String afterValue;

    /** Resolved by {@code DefaultAuditTrail} from the current request; never supplied by a caller. */
    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    /** Resolved by {@code DefaultAuditTrail} from {@code CorrelationIdFilter}; never supplied by a caller. */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    protected AuditEvent() {
        // for JPA
    }

    public AuditEvent(UUID actorUserId, String action, String entityType, UUID entityId, String details) {
        this(actorUserId, null, action, entityType, entityId, details);
    }

    public AuditEvent(
            UUID actorUserId, String actorLabel, String action, String entityType, UUID entityId, String details) {
        this(actorUserId, actorLabel, action, entityType, entityId, details, null, null, null);
    }

    public AuditEvent(
            UUID actorUserId,
            String actorLabel,
            String action,
            String entityType,
            UUID entityId,
            String details,
            String reason,
            String beforeValue,
            String afterValue) {
        this.actorUserId = actorUserId;
        this.actorLabel = actorLabel;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.reason = reason;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
    }

    /** Set once, immediately after construction, by {@code DefaultAuditTrail} alone. */
    public void resolvedFrom(String sourceIp, String correlationId) {
        this.sourceIp = sourceIp;
        this.correlationId = correlationId;
    }
}
