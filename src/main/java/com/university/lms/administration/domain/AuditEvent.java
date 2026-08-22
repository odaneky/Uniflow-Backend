package com.university.lms.administration.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

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

    protected AuditEvent() {
        // for JPA
    }

    public AuditEvent(UUID actorUserId, String action, String entityType, UUID entityId, String details) {
        this(actorUserId, null, action, entityType, entityId, details);
    }

    public AuditEvent(
            UUID actorUserId, String actorLabel, String action, String entityType, UUID entityId, String details) {
        this.actorUserId = actorUserId;
        this.actorLabel = actorLabel;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
    }
}
