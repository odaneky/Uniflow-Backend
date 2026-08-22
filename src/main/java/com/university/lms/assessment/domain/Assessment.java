package com.university.lms.assessment.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * A piece of assessed work set for a course section.
 *
 * <p>Carries what the work is worth, not what anyone scored: the resulting mark is a {@code Grade}
 * in the grading module. Keeping the two apart is what allows a grade to be moderated, appealed or
 * recomputed without touching the assessment definition.
 */
@Entity
@Table(
        name = "assessments",
        indexes = {
            @Index(name = "idx_assessments_section", columnList = "course_section_id"),
            @Index(name = "idx_assessments_due", columnList = "due_at")
        })
@Getter
public class Assessment extends BaseEntity {

    /** Cross-module reference into course. */
    @Column(name = "course_section_id", nullable = false)
    private UUID courseSectionId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "instructions", length = 4000)
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type", nullable = false, length = 30)
    private AssessmentType assessmentType;

    @Column(name = "max_score", nullable = false, precision = 7, scale = 2)
    private BigDecimal maxScore;

    /** Contribution to the section's final mark, as a percentage. */
    @Column(name = "weight_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightPercent;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "published", nullable = false)
    private boolean published;

    /** Soft time limit for quizzes/exams; null means untimed. Not enforced in v1. */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /** Percentage of max score required to pass; null means not specified. */
    @Column(name = "pass_mark_percent", precision = 5, scale = 2)
    private BigDecimal passMarkPercent;

    /**
     * When true, students who have submitted may see which options were correct and
     * whether their objective answers matched.
     */
    @Column(name = "show_correct_answers", nullable = false)
    private boolean showCorrectAnswers;

    /** Publishing and due-date changes race with concurrent submissions. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Assessment() {
        // for JPA
    }

    public Assessment(
            UUID courseSectionId,
            String title,
            AssessmentType assessmentType,
            BigDecimal maxScore,
            BigDecimal weightPercent) {
        this.courseSectionId = courseSectionId;
        this.title = title;
        this.assessmentType = assessmentType;
        this.maxScore = maxScore;
        this.weightPercent = weightPercent;
    }

    public void describe(String instructions) {
        this.instructions = instructions;
    }

    public void schedule(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public void publish() {
        this.published = true;
    }

    public void retitle(String title) {
        this.title = title;
    }

    public void reweight(BigDecimal weightPercent) {
        this.weightPercent = weightPercent;
    }

    public void setMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public void setPassMarkPercent(BigDecimal passMarkPercent) {
        this.passMarkPercent = passMarkPercent;
    }

    public void setShowCorrectAnswers(boolean showCorrectAnswers) {
        this.showCorrectAnswers = showCorrectAnswers;
    }

    public boolean isOverdue(Instant at) {
        return dueAt != null && at.isAfter(dueAt);
    }

    public boolean isQuizLike() {
        return assessmentType == AssessmentType.QUIZ || assessmentType == AssessmentType.EXAM;
    }
}
