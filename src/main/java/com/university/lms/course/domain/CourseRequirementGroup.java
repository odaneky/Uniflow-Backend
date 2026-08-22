package com.university.lms.course.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(
        name = "course_requirement_groups",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_course_requirement_groups", columnNames = {"course_id", "position"}))
@Getter
public class CourseRequirementGroup extends BaseEntity {

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "position", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private RequirementKind kind;

    @Column(name = "minimum_level")
    private Integer minimumLevel;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "course_requirement_options",
            joinColumns =
                    @JoinColumn(
                            name = "group_id",
                            nullable = false,
                            foreignKey = @ForeignKey(name = "fk_course_requirement_options_group")))
    @Column(name = "required_course_id", nullable = false)
    private Set<UUID> optionCourseIds = new LinkedHashSet<>();

    protected CourseRequirementGroup() {
        // for JPA
    }

    public CourseRequirementGroup(
            UUID courseId, int position, RequirementKind kind, Integer minimumLevel, Set<UUID> optionCourseIds) {
        this.courseId = courseId;
        this.position = position;
        this.kind = kind;
        this.minimumLevel = minimumLevel;
        this.optionCourseIds = new LinkedHashSet<>(optionCourseIds);
    }
}
