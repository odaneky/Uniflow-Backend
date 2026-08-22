package com.university.lms.assessment.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(
        name = "quiz_options",
        indexes = @Index(name = "idx_quiz_options_question", columnList = "question_id"))
@Getter
public class QuizOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "question_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_quiz_options_question"))
    private QuizQuestion question;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "label", nullable = false, length = 1000)
    private String label;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    protected QuizOption() {}

    public QuizOption(QuizQuestion question, int position, String label, boolean correct) {
        this.question = question;
        this.position = position;
        this.label = label;
        this.correct = correct;
    }

    public void moveTo(int position) {
        this.position = position;
    }

    public void relabel(String label) {
        this.label = label;
    }

    public void markCorrect(boolean correct) {
        this.correct = correct;
    }
}
