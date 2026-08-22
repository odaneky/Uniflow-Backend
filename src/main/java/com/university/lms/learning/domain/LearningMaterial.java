package com.university.lms.learning.domain;

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
import java.util.UUID;
import lombok.Getter;

/**
 * A resource attached to a {@link Lesson}.
 *
 * <p>Never holds file bytes. A material either points outward via {@code externalUrl} or refers to
 * a {@code document} row that carries the storage key for an object store — see the document
 * module.
 */
@Entity
@Table(name = "learning_materials", indexes = @Index(name = "idx_learning_materials_lesson", columnList = "lesson_id"))
@Getter
public class LearningMaterial extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, foreignKey = @ForeignKey(name = "fk_learning_materials_lesson"))
    private Lesson lesson;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 30)
    private MaterialType materialType;

    @Column(name = "external_url", length = 1000)
    private String externalUrl;

    /** Cross-module reference into document, when the material is a stored file. */
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "position", nullable = false)
    private int position;

    protected LearningMaterial() {
        // for JPA
    }

    public LearningMaterial(Lesson lesson, String title, MaterialType materialType, int position) {
        this.lesson = lesson;
        this.title = title;
        this.materialType = materialType;
        this.position = position;
    }

    public void linkTo(String externalUrl) {
        this.externalUrl = externalUrl;
        this.documentId = null;
    }

    public void attach(UUID documentId) {
        this.documentId = documentId;
        this.externalUrl = null;
    }
}
