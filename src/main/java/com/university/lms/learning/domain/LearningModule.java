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

/**
 * A teaching unit within a course's content, e.g. "Module 3 — Relational Algebra".
 *
 * <p>Named {@code LearningModule} rather than {@code Module}: {@code java.lang.Module} is imported
 * into every compilation unit automatically, so a domain type called {@code Module} shadows it and
 * produces genuinely confusing errors in unrelated code.
 */
@Entity
@Table(
        name = "learning_modules",
        indexes = @Index(name = "idx_learning_modules_content", columnList = "course_content_id"))
@Getter
public class LearningModule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "course_content_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_learning_modules_content"))
    private CourseContent courseContent;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** Display order within the course; not a database identity. */
    @Column(name = "position", nullable = false)
    private int position;

    /** Drafts are visible to teaching staff only until published. */
    @Column(name = "published", nullable = false)
    private boolean published;

    protected LearningModule() {
        // for JPA
    }

    public LearningModule(CourseContent courseContent, String title, int position) {
        this.courseContent = courseContent;
        this.title = title;
        this.position = position;
    }

    public void publish() {
        this.published = true;
    }

    public void unpublish() {
        this.published = false;
    }

    public void moveTo(int position) {
        this.position = position;
    }
}
