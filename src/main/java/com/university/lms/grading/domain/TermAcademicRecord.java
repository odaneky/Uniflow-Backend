package com.university.lms.grading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * One student's standing at the close of one term — written once, by {@code TermCloseService}, and
 * never recomputed. Reading this instead of re-deriving from every grade is what makes a
 * transcript, a standing decision or a SAP check reproducible: the answer this row gives cannot
 * change just because a later term's grades were entered.
 *
 * <p>No mutator beyond construction. A correction to a closed term is a new close, which — like
 * every other write here — {@code TermCloseService} makes idempotent by skipping any student who
 * already has a row for that term, not by rewriting one.
 */
@Entity
@Table(
        name = "term_academic_records",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_term_academic_records",
                        columnNames = {"student_id", "academic_term_id"}),
        indexes = {
            @Index(name = "idx_term_academic_records_student", columnList = "student_id"),
            @Index(name = "idx_term_academic_records_term", columnList = "academic_term_id")
        })
@Getter
public class TermAcademicRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "academic_term_id", nullable = false)
    private UUID academicTermId;

    @Column(name = "term_order", nullable = false)
    private int termOrder;

    @Column(name = "term_gpa", precision = 4, scale = 2)
    private BigDecimal termGpa;

    @Column(name = "cumulative_gpa", precision = 4, scale = 2)
    private BigDecimal cumulativeGpa;

    @Column(name = "credits_attempted", nullable = false)
    private int creditsAttempted;

    @Column(name = "credits_earned", nullable = false)
    private int creditsEarned;

    @Column(name = "cumulative_credits_earned", nullable = false)
    private int cumulativeCreditsEarned;

    @Enumerated(EnumType.STRING)
    @Column(name = "standing", nullable = false, length = 30)
    private AcademicStanding standing;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected TermAcademicRecord() {
        // for JPA
    }

    public TermAcademicRecord(
            UUID studentId,
            UUID academicTermId,
            int termOrder,
            BigDecimal termGpa,
            BigDecimal cumulativeGpa,
            int creditsAttempted,
            int creditsEarned,
            int cumulativeCreditsEarned,
            AcademicStanding standing) {
        this.studentId = studentId;
        this.academicTermId = academicTermId;
        this.termOrder = termOrder;
        this.termGpa = termGpa;
        this.cumulativeGpa = cumulativeGpa;
        this.creditsAttempted = creditsAttempted;
        this.creditsEarned = creditsEarned;
        this.cumulativeCreditsEarned = cumulativeCreditsEarned;
        this.standing = standing;
        this.computedAt = Instant.now();
    }
}
