package com.university.lms.disciplinary.domain;

import com.university.lms.common.audit.BaseEntity;
import com.university.lms.common.exception.BusinessException;
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
 * A disciplinary matter against a student. Confidentiality is enforced by {@code
 * DisciplinaryCaseService}, not here — this entity only holds the state; who may read it depends
 * on who is signed in, not on anything the record itself could check.
 */
@Entity
@Table(
        name = "disciplinary_cases",
        indexes = {
            @Index(name = "idx_disciplinary_cases_student", columnList = "student_id,filed_at"),
            @Index(name = "idx_disciplinary_cases_officer", columnList = "assigned_officer_user_id")
        })
@Getter
public class DisciplinaryCase extends BaseEntity {

    @Column(name = "case_number", nullable = false, length = 20)
    private String caseNumber;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private DisciplinaryCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DisciplinaryCaseStatus status;

    @Column(name = "summary", nullable = false, length = 2000)
    private String summary;

    @Column(name = "filed_by_user_id", nullable = false)
    private UUID filedByUserId;

    @Column(name = "assigned_officer_user_id")
    private UUID assignedOfficerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 30)
    private DisciplinaryOutcome outcome;

    @Column(name = "outcome_reason", length = 2000)
    private String outcomeReason;

    @Column(name = "filed_at", nullable = false)
    private Instant filedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected DisciplinaryCase() {}

    public DisciplinaryCase(
            String caseNumber, UUID studentId, DisciplinaryCategory category, String summary, UUID filedByUserId) {
        this.caseNumber = caseNumber;
        this.studentId = studentId;
        this.category = category;
        this.status = DisciplinaryCaseStatus.OPEN;
        this.summary = summary;
        this.filedByUserId = filedByUserId;
        this.filedAt = Instant.now();
    }

    /** The filer and the assigned officer both keep access for as long as the case is open — filing it does not hand it off. */
    public boolean isReadableBy(UUID userId) {
        return userId.equals(filedByUserId) || userId.equals(assignedOfficerUserId);
    }

    public void assignOfficer(UUID officerUserId) {
        this.assignedOfficerUserId = officerUserId;
        if (status == DisciplinaryCaseStatus.OPEN) {
            this.status = DisciplinaryCaseStatus.UNDER_REVIEW;
        }
    }

    public void close(DisciplinaryCaseStatus target, DisciplinaryOutcome outcome, String reason) {
        if (target != DisciplinaryCaseStatus.RESOLVED && target != DisciplinaryCaseStatus.DISMISSED) {
            throw new BusinessException(
                    DisciplinaryErrorCode.INVALID_CASE_TRANSITION, "A case can only be closed as resolved or dismissed");
        }
        if (!status.canTransitionTo(target)) {
            throw new BusinessException(
                    DisciplinaryErrorCode.INVALID_CASE_TRANSITION,
                    "Cannot move a case from " + status + " to " + target);
        }
        if (outcome == null || reason == null || reason.isBlank()) {
            throw new BusinessException(
                    DisciplinaryErrorCode.CASE_OUTCOME_REQUIRED, "Closing a case requires an outcome and a reason");
        }
        this.status = target;
        this.outcome = outcome;
        this.outcomeReason = reason;
        this.resolvedAt = Instant.now();
    }
}
