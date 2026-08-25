package com.university.lms.grading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/**
 * One term's standing decision for one student — kept even when standing did not change, so "was
 * this student reviewed at this term's close" has an answer independent of the outcome.
 *
 * <p>Separate from {@link TermAcademicRecord}, which stores the computed standing as part of the
 * term's frozen record: this is the decision log — what changed, why, and (when a committee
 * overrides the system-derived outcome) who decided it. No mutator beyond construction; a later
 * correction is a new event for a later term, not a rewritten one.
 */
@Entity
@Table(
        name = "academic_standing_events",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_academic_standing_events",
                        columnNames = {"student_id", "academic_term_id"}),
        indexes = {
            @Index(name = "idx_academic_standing_events_student", columnList = "student_id"),
            @Index(name = "idx_academic_standing_events_term", columnList = "academic_term_id")
        })
@Getter
public class AcademicStandingEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "academic_term_id", nullable = false)
    private UUID academicTermId;

    /** Snapshotted at write, same as {@code Grade.termOrder}: sorts "most recent" correctly even
     * when two terms are closed on the same calendar day. */
    @Column(name = "term_order", nullable = false)
    private int termOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_standing", length = 30)
    private AcademicStanding fromStanding;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_standing", nullable = false, length = 30)
    private AcademicStanding toStanding;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    /** Cross-module reference into identity; null for a system-derived outcome. */
    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Cross-module reference into request; null unless this standing was set through an appeal. */
    @Column(name = "appeal_request_id")
    private UUID appealRequestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AcademicStandingEvent() {
        // for JPA
    }

    public AcademicStandingEvent(
            UUID studentId,
            UUID academicTermId,
            int termOrder,
            AcademicStanding fromStanding,
            AcademicStanding toStanding,
            String reason,
            UUID decidedBy,
            LocalDate effectiveFrom,
            UUID appealRequestId) {
        this.studentId = studentId;
        this.academicTermId = academicTermId;
        this.termOrder = termOrder;
        this.fromStanding = fromStanding;
        this.toStanding = toStanding;
        this.reason = reason;
        this.decidedBy = decidedBy;
        this.effectiveFrom = effectiveFrom;
        this.appealRequestId = appealRequestId;
        this.createdAt = Instant.now();
    }
}
