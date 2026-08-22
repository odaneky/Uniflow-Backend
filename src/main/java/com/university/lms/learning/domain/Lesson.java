package com.university.lms.learning.domain;

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

/** A single session or topic within a {@link LearningModule}. */
@Entity
@Table(name = "lessons", indexes = @Index(name = "idx_lessons_module", columnList = "learning_module_id"))
@Getter
public class Lesson extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "learning_module_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_lessons_module"))
    private LearningModule learningModule;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "summary", length = 2000)
    private String summary;

    @Column(name = "position", nullable = false)
    private int position;

    protected Lesson() {
        // for JPA
    }

    public Lesson(LearningModule learningModule, String title, int position) {
        this.learningModule = learningModule;
        this.title = title;
        this.position = position;
    }

    public void summarise(String summary) {
        this.summary = summary;
    }

    public void moveTo(int position) {
        this.position = position;
    }
}
