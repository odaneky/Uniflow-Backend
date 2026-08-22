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
import java.math.BigDecimal;
import lombok.Getter;

@Entity
@Table(
        name = "quiz_questions",
        indexes = @Index(name = "idx_quiz_questions_assessment", columnList = "assessment_id"))
@Getter
public class QuizQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "assessment_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_quiz_questions_assessment"))
    private Assessment assessment;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "prompt", nullable = false, length = 4000)
    private String prompt;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 30)
    private QuizQuestionType questionType;

    @Column(name = "points", nullable = false, precision = 7, scale = 2)
    private BigDecimal points;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_mode", length = 30)
    private QuizScoringMode scoringMode;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    protected QuizQuestion() {}

    public QuizQuestion(
            Assessment assessment,
            int position,
            String prompt,
            QuizQuestionType questionType,
            BigDecimal points) {
        this.assessment = assessment;
        this.position = position;
        this.prompt = prompt;
        this.questionType = questionType;
        this.points = points;
    }

    public void moveTo(int position) {
        this.position = position;
    }

    public void reword(String prompt) {
        this.prompt = prompt;
    }

    public void setPoints(BigDecimal points) {
        this.points = points;
    }

    public void setScoringMode(QuizScoringMode scoringMode) {
        this.scoringMode = scoringMode;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }
}
