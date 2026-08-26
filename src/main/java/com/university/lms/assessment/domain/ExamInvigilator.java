package com.university.lms.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** G6: a staff member assigned to invigilate an exam sitting. */
@Entity
@Table(name = "exam_invigilators")
@IdClass(ExamInvigilator.ExamInvigilatorId.class)
@Getter
public class ExamInvigilator {

    @Id
    @Column(name = "exam_sitting_id", nullable = false)
    private UUID examSittingId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    protected ExamInvigilator() {
        // for JPA
    }

    public ExamInvigilator(UUID examSittingId, UUID userId, UUID assignedBy) {
        this.examSittingId = examSittingId;
        this.userId = userId;
        this.assignedBy = assignedBy;
        this.assignedAt = Instant.now();
    }

    public record ExamInvigilatorId(UUID examSittingId, UUID userId) implements Serializable {}
}
