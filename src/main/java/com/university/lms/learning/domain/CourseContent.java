package com.university.lms.learning.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;

/**
 * Root of the teaching material for one course section: CourseContent → LearningModule → Lesson →
 * LearningMaterial.
 *
 * <p>Attached to a section rather than to a course, because two lecturers teaching the same course
 * in the same term legitimately publish different material.
 */
@Entity
@Table(
        name = "course_contents",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_course_contents_section", columnNames = "course_section_id"))
@Getter
public class CourseContent extends BaseEntity {

    /** Cross-module reference into course. */
    @Column(name = "course_section_id", nullable = false)
    private UUID courseSectionId;

    @Column(name = "overview", length = 4000)
    private String overview;

    protected CourseContent() {
        // for JPA
    }

    public CourseContent(UUID courseSectionId) {
        this.courseSectionId = courseSectionId;
    }

    public void describe(String overview) {
        this.overview = overview;
    }
}
