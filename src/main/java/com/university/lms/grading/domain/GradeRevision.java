package com.university.lms.grading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * One award or change to a {@link Grade}, kept forever.
 *
 * <p>{@code before*} is null only on the first revision of a grade — the initial award has nothing
 * to compare against. Every later row carries both sides of the change, a mandatory {@code reason},
 * and who made it. Rows are never updated or deleted: {@code GradeRevisionRepository} exposes only
 * {@code save} and finders, not the full {@code JpaRepository} surface, so there is no method on the
 * repository that could even attempt one.
 */
@Entity
@Table(
        name = "grade_revisions",
        indexes = {@Index(name = "idx_grade_revisions_grade", columnList = "grade_id")})
@Getter
public class GradeRevision {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_id", nullable = false, foreignKey = @ForeignKey(name = "fk_grade_revisions_grade"))
    private Grade grade;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "before_percentage", precision = 5, scale = 2)
    private BigDecimal beforePercentage;

    @Column(name = "before_letter", length = 5)
    private String beforeLetter;

    @Column(name = "before_grade_point", precision = 4, scale = 2)
    private BigDecimal beforeGradePoint;

    @Column(name = "after_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal afterPercentage;

    @Column(name = "after_letter", nullable = false, length = 5)
    private String afterLetter;

    @Column(name = "after_grade_point", nullable = false, precision = 4, scale = 2)
    private BigDecimal afterGradePoint;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    /** Cross-module reference into identity. */
    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    /**
     * Set only when this revision came through the review workflow that unlocks a locked grade; the
     * approver must differ from {@link #changedBy}. Null for an ordinary, unlocked revision.
     */
    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected GradeRevision() {
        // for JPA
    }

    public GradeRevision(
            Grade grade,
            int revisionNumber,
            BigDecimal beforePercentage,
            String beforeLetter,
            BigDecimal beforeGradePoint,
            BigDecimal afterPercentage,
            String afterLetter,
            BigDecimal afterGradePoint,
            String reason,
            UUID changedBy,
            UUID approvedBy) {
        this.grade = grade;
        this.revisionNumber = revisionNumber;
        this.beforePercentage = beforePercentage;
        this.beforeLetter = beforeLetter;
        this.beforeGradePoint = beforeGradePoint;
        this.afterPercentage = afterPercentage;
        this.afterLetter = afterLetter;
        this.afterGradePoint = afterGradePoint;
        this.reason = reason;
        this.changedBy = changedBy;
        this.approvedBy = approvedBy;
        this.changedAt = Instant.now();
    }
}
