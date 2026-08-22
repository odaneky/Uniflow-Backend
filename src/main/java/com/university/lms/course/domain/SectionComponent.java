package com.university.lms.course.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;

/**
 * One teaching activity inside an occurrence set.
 *
 * <p>UN1 is the occurrence. Lecture, tutorial, and laboratory are rows here — each with its own
 * seats and teacher. Students still enrol in the occurrence, not in a component.
 */
@Entity
@Table(
        name = "section_components",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_section_components_section_kind",
                        columnNames = {"section_id", "component"}))
@Getter
public class SectionComponent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "section_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_section_components_section"))
    private CourseSection section;

    @Enumerated(EnumType.STRING)
    @Column(name = "component", nullable = false, length = 20)
    private CourseComponent component;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "lecturer_user_id")
    private UUID lecturerUserId;

    protected SectionComponent() {}

    public SectionComponent(CourseSection section, CourseComponent component, int capacity, UUID lecturerUserId) {
        this.section = section;
        this.component = component;
        this.capacity = capacity;
        this.lecturerUserId = lecturerUserId;
    }
}
