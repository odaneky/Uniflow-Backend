package com.university.lms.grading.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * An awarded mark.
 *
 * <p>{@code assessmentId} is nullable and deliberately a plain identifier: a grade may record a
 * single assessment or the overall result for a section, and grading must not become unusable if
 * the assessment module changes shape. Awarding a grade does not require an assessment to exist at
 * all — which is what keeps the two domains genuinely decoupled, as required.
 */
@Entity
@Table(
        name = "grades",
        indexes = {
            @Index(name = "idx_grades_student", columnList = "student_id"),
            @Index(name = "idx_grades_section", columnList = "course_section_id"),
            @Index(name = "idx_grades_assessment", columnList = "assessment_id")
        })
@Getter
public class Grade extends BaseEntity {

    /** Cross-module reference into student. */
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /** Cross-module reference into course. */
    @Column(name = "course_section_id", nullable = false)
    private UUID courseSectionId;

    /**
     * Cross-module reference into course — snapshotted at award, alongside {@link #credits} and
     * {@link #termOrder} below. Never revised: a later correction to the mark does not mean the
     * course, term or credit value it was awarded for changed.
     */
    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /** Cross-module reference into academic. Snapshotted at award; see {@link #courseId}. */
    @Column(name = "academic_term_id", nullable = false)
    private UUID academicTermId;

    /**
     * The course's credit value at the moment this grade was awarded. GPA is computed from this,
     * not from a live lookup — editing {@code courses.credits} later must not silently rewrite
     * every historic GPA that counted it.
     */
    @Column(name = "credits", nullable = false)
    private int credits;

    /**
     * The term's institutional chronological position at award — see
     * {@code AcademicStructure.termOrdinal}. A transcript sorts by this, not by
     * {@code created_at}, so a mark entered late does not appear to have happened earlier or later
     * than it was actually for.
     */
    @Column(name = "term_order", nullable = false)
    private int termOrder;

    /** Cross-module reference into assessment; null for an overall section result. */
    @Column(name = "assessment_id")
    private UUID assessmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_scale_id", nullable = false, foreignKey = @ForeignKey(name = "fk_grades_scale"))
    private GradeScale gradeScale;

    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "letter", nullable = false, length = 5)
    private String letter;

    @Column(name = "grade_point", nullable = false, precision = 4, scale = 2)
    private BigDecimal gradePoint;

    /** Provisional until moderation completes; only published grades are visible to students. */
    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "under_appeal", nullable = false)
    private boolean underAppeal;

    /** Set at term close; once locked, {@link #revise} refuses until the grade is unlocked. */
    @Column(name = "locked_at")
    private Instant lockedAt;

    /** Cross-module reference into identity. */
    @Column(name = "locked_by")
    private UUID lockedBy;

    /**
     * Grade changes are the single most contended and most consequential write in the system —
     * a lost update here silently alters an academic record.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Grade() {
        // for JPA
    }

    public Grade(
            UUID studentId,
            UUID courseSectionId,
            GradeScale gradeScale,
            BigDecimal percentage,
            String letter,
            BigDecimal gradePoint,
            UUID courseId,
            UUID academicTermId,
            int credits,
            int termOrder) {
        this.studentId = studentId;
        this.courseSectionId = courseSectionId;
        this.gradeScale = gradeScale;
        this.percentage = percentage;
        this.letter = letter;
        this.gradePoint = gradePoint;
        this.courseId = courseId;
        this.academicTermId = academicTermId;
        this.credits = credits;
        this.termOrder = termOrder;
    }

    public void forAssessment(UUID assessmentId) {
        this.assessmentId = assessmentId;
    }

    public void publish() {
        this.published = true;
    }

    public void revise(BigDecimal percentage, String letter, BigDecimal gradePoint) {
        if (isLocked()) {
            throw new IllegalStateException("This grade is locked and cannot be revised directly");
        }
        this.percentage = percentage;
        this.letter = letter;
        this.gradePoint = gradePoint;
    }

    public void markUnderAppeal() {
        this.underAppeal = true;
    }

    public void clearUnderAppeal() {
        this.underAppeal = false;
    }

    public boolean isLocked() {
        return lockedAt != null;
    }

    public void lock(UUID lockedBy) {
        this.lockedAt = Instant.now();
        this.lockedBy = lockedBy;
    }
}
