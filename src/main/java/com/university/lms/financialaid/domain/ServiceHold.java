package com.university.lms.financialaid.domain;

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

@Entity
@Table(
        name = "service_holds",
        indexes = @Index(name = "idx_service_holds_student_active", columnList = "student_id, active, placed_at"))
@Getter
public class ServiceHold extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "hold_type", nullable = false, length = 20)
    private HoldType holdType;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "cleared_at")
    private Instant clearedAt;

    @Column(name = "placed_by")
    private UUID placedBy;

    protected ServiceHold() {}

    public ServiceHold(UUID studentId, HoldType holdType, String reason, Instant placedAt, UUID placedBy) {
        this.studentId = studentId;
        this.holdType = holdType;
        this.reason = reason;
        this.placedAt = placedAt;
        this.placedBy = placedBy;
    }

    public void clear(Instant at) {
        if (!active) {
            throw new IllegalStateException("Hold is already cleared");
        }
        active = false;
        clearedAt = at;
    }
}
