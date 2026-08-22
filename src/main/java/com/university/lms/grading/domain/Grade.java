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
            BigDecimal gradePoint) {
        this.studentId = studentId;
        this.courseSectionId = courseSectionId;
        this.gradeScale = gradeScale;
        this.percentage = percentage;
        this.letter = letter;
        this.gradePoint = gradePoint;
    }

    public void forAssessment(UUID assessmentId) {
        this.assessmentId = assessmentId;
    }

    public void publish() {
        this.published = true;
    }

    public void revise(BigDecimal percentage, String letter, BigDecimal gradePoint) {
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
}
