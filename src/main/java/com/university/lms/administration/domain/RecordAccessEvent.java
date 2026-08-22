package com.university.lms.administration.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Append-only log of staff access to student education records (FERPA). */
@Entity
@Table(
        name = "record_access_events",
        indexes = {
            @Index(name = "idx_record_access_student", columnList = "student_id,accessed_at"),
            @Index(name = "idx_record_access_actor", columnList = "actor_user_id,accessed_at")
        })
@Getter
public class RecordAccessEvent extends BaseEntity {

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "actor_label", length = 200)
    private String actorLabel;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "record_type", nullable = false, length = 60)
    private String recordType;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "details", length = 500)
    private String details;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;

    protected RecordAccessEvent() {}

    public RecordAccessEvent(
            UUID actorUserId,
            String actorLabel,
            UUID studentId,
            String recordType,
            String action,
            String details,
            Instant accessedAt) {
        this.actorUserId = actorUserId;
        this.actorLabel = actorLabel;
        this.studentId = studentId;
        this.recordType = recordType;
        this.action = action;
        this.details = details;
        this.accessedAt = accessedAt;
    }
}
