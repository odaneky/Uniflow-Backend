package com.university.lms.assessment.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(
        name = "quiz_answers",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_quiz_answers_attempt_question",
                        columnNames = {"attempt_id", "question_id"}),
        indexes = {
            @Index(name = "idx_quiz_answers_attempt", columnList = "attempt_id"),
            @Index(name = "idx_quiz_answers_question", columnList = "question_id")
        })
@Getter
public class QuizAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "attempt_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_quiz_answers_attempt"))
    private AssessmentAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "question_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_quiz_answers_question"))
    private QuizQuestion question;

    @Column(name = "text_response", length = 8000)
    private String textResponse;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "auto_score", precision = 7, scale = 2)
    private BigDecimal autoScore;

    @Column(name = "manual_score", precision = 7, scale = 2)
    private BigDecimal manualScore;

    @Column(name = "feedback", length = 2000)
    private String feedback;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "quiz_answer_options",
            joinColumns =
                    @JoinColumn(
                            name = "answer_id",
                            foreignKey = @ForeignKey(name = "fk_quiz_answer_options_answer")))
    @Column(name = "option_id", nullable = false)
    private Set<UUID> selectedOptionIds = new HashSet<>();

    protected QuizAnswer() {}

    public QuizAnswer(AssessmentAttempt attempt, QuizQuestion question) {
        this.attempt = attempt;
        this.question = question;
    }

    public void setTextResponse(String textResponse) {
        this.textResponse = textResponse;
    }

    public void attachDocument(UUID documentId) {
        this.documentId = documentId;
    }

    public void replaceSelections(Set<UUID> optionIds) {
        this.selectedOptionIds.clear();
        if (optionIds != null) {
            this.selectedOptionIds.addAll(optionIds);
        }
    }

    public void setAutoScore(BigDecimal autoScore) {
        this.autoScore = autoScore;
    }

    public void gradeManually(BigDecimal manualScore, String feedback) {
        this.manualScore = manualScore;
        this.feedback = feedback;
    }

    /** Effective points for totals: manual overrides auto when set. */
    public BigDecimal effectiveScore() {
        if (manualScore != null) {
            return manualScore;
        }
        return autoScore;
    }
}
