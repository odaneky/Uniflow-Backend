package com.university.lms.assessment.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * One student's attempt at an {@link Assessment}.
 *
 * <p>Attempts are numbered and the triple (assessment, student, attempt number) is unique, so a
 * retake is a new row rather than an overwrite — the earlier attempt stays auditable.
 */
@Entity
@Table(
        name = "assessment_attempts",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_assessment_attempts_assessment_student_number",
                        columnNames = {"assessment_id", "student_id", "attempt_number"}),
        indexes = {
            @Index(name = "idx_assessment_attempts_assessment", columnList = "assessment_id"),
            @Index(name = "idx_assessment_attempts_student", columnList = "student_id"),
            @Index(name = "idx_assessment_attempts_document", columnList = "document_id")
        })
@Getter
public class AssessmentAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "assessment_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_assessment_attempts_assessment"))
    private Assessment assessment;

    /** Cross-module reference into student. */
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** Raw mark before scaling; the published grade lives in the grading module. */
    @Column(name = "raw_score", precision = 7, scale = 2)
    private BigDecimal rawScore;

    /** Cross-module reference into document — the uploaded artefact, if any. */
    @Column(name = "document_id")
    private UUID documentId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AssessmentAttempt() {
        // for JPA
    }

    public AssessmentAttempt(Assessment assessment, UUID studentId, int attemptNumber) {
        this.assessment = assessment;
        this.studentId = studentId;
        this.attemptNumber = attemptNumber;
    }

    public void attachDocument(UUID documentId) {
        this.documentId = documentId;
    }

    public void submit(Instant at) {
        this.submittedAt = at;
        this.status = assessment.isOverdue(at) ? AttemptStatus.LATE : AttemptStatus.SUBMITTED;
    }

    public void recordScore(BigDecimal rawScore) {
        this.rawScore = rawScore;
        this.status = AttemptStatus.GRADED;
    }

    /** Updates known points without marking the attempt fully graded (pending manual review). */
    public void recordPartialScore(BigDecimal rawScore) {
        this.rawScore = rawScore;
    }
}
