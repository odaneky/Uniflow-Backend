package com.university.lms.curriculum.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/**
 * The formal conferral record of a degree — separate from {@code students.status} flipping to
 * {@code GRADUATED}, which records only the enrolment-status side effect. This is the historical
 * evidence: the programme, the curriculum version it was awarded against, the cumulative GPA and
 * credits at that moment, and any Latin honours — none of which {@code students.status} alone can
 * ever answer once later grades or requirement changes land.
 *
 * <p>No mutator beyond construction: a conferral, once recorded, is never edited — the same
 * immutability the rest of this pass's temporal work already established for {@code
 * grade_revisions} and {@code academic_standing_events}.
 */
@Entity
@Table(
        name = "degree_awards",
        indexes = @Index(name = "idx_degree_awards_student", columnList = "student_id"))
@Getter
public class DegreeAward extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;

    /** The curriculum version the audit was resolved against at conferral; null if the student was never bound. */
    @Column(name = "curriculum_version_id")
    private UUID curriculumVersionId;

    @Column(name = "degree_award_label", nullable = false, length = 50)
    private String degreeAwardLabel;

    @Column(name = "conferred_on", nullable = false)
    private LocalDate conferredOn;

    @Column(name = "gpa_at_conferral", precision = 4, scale = 2)
    private BigDecimal gpaAtConferral;

    @Column(name = "credits_earned_at_conferral", nullable = false)
    private int creditsEarnedAtConferral;

    @Enumerated(EnumType.STRING)
    @Column(name = "honours", length = 30)
    private Honours honours;

    /** Cross-module reference into identity: who processed the conferral. */
    @Column(name = "conferred_by")
    private UUID conferredBy;

    protected DegreeAward() {
        // for JPA
    }

    public DegreeAward(
            UUID studentId,
            UUID programmeId,
            UUID curriculumVersionId,
            String degreeAwardLabel,
            LocalDate conferredOn,
            BigDecimal gpaAtConferral,
            int creditsEarnedAtConferral,
            Honours honours,
            UUID conferredBy) {
        this.studentId = studentId;
        this.programmeId = programmeId;
        this.curriculumVersionId = curriculumVersionId;
        this.degreeAwardLabel = degreeAwardLabel;
        this.conferredOn = conferredOn;
        this.gpaAtConferral = gpaAtConferral;
        this.creditsEarnedAtConferral = creditsEarnedAtConferral;
        this.honours = honours;
        this.conferredBy = conferredBy;
    }
}
