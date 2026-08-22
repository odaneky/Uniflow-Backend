package com.university.lms.curriculum.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(
        name = "graduation_clearance_items",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_graduation_clearance_student_type",
                        columnNames = {"student_id", "item_type"}),
        indexes = @Index(name = "idx_graduation_clearance_student", columnList = "student_id"))
@Getter
public class GraduationClearanceItem extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "item_type", nullable = false, length = 50)
    private String itemType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private GraduationClearanceStatus status = GraduationClearanceStatus.PENDING;

    @Column(name = "cleared_at")
    private Instant clearedAt;

    @Column(name = "cleared_by")
    private UUID clearedBy;

    @Column(name = "note", length = 500)
    private String note;

    protected GraduationClearanceItem() {
        // for JPA
    }

    public GraduationClearanceItem(UUID studentId, String itemType) {
        this.studentId = studentId;
        this.itemType = itemType;
    }

    public void clear(UUID actorUserId, String note) {
        this.status = GraduationClearanceStatus.CLEARED;
        this.clearedAt = Instant.now();
        this.clearedBy = actorUserId;
        this.note = note;
    }

    public void waive(UUID actorUserId, String note) {
        this.status = GraduationClearanceStatus.WAIVED;
        this.clearedAt = Instant.now();
        this.clearedBy = actorUserId;
        this.note = note;
    }
}
