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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;

/**
 * A reusable course definition in the catalog, e.g. {@code COMP3101 Software Engineering}.
 *
 * <p>A course is <em>not</em> a thing students enrol in. It is the durable definition; what a
 * student joins is a {@link CourseSection} — a specific offering of this course, in a specific
 * term, with a lecturer and a capacity. Conflating the two is the single most common modelling
 * mistake in a student system, and it makes historical records unrepresentable the moment a course
 * is taught twice.
 */
@Entity
@Table(
        name = "courses",
        uniqueConstraints = @UniqueConstraint(name = "uk_courses_course_code", columnNames = "course_code"),
        indexes = {
            @Index(name = "idx_courses_department", columnList = "department_id"),
            @Index(name = "idx_courses_status", columnList = "status")
        })
@Getter
public class Course extends BaseEntity {

    @Column(name = "course_code", nullable = false, length = 20)
    private String courseCode;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "credits", nullable = false)
    private int credits;

    /** Study level, e.g. 1 for a first-year course. */
    @Column(name = "level", nullable = false)
    private int level;

    /** Cross-module reference into academic. */
    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "course_components",
            joinColumns =
                    @JoinColumn(
                            name = "course_id",
                            nullable = false,
                            foreignKey = @ForeignKey(name = "fk_course_components_course")))
    @Enumerated(EnumType.STRING)
    @Column(name = "component", nullable = false, length = 20)
    private Set<CourseComponent> components = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CourseStatus status = CourseStatus.DRAFT;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Course() {
        // for JPA
    }

    public Course(
            String courseCode,
            String title,
            int credits,
            int level,
            UUID departmentId,
            Collection<CourseComponent> components) {
        this.courseCode = courseCode;
        this.title = title;
        this.credits = credits;
        this.level = level;
        this.departmentId = departmentId;
        replaceComponents(components);
    }

    public boolean isOfferable() {
        return status == CourseStatus.ACTIVE;
    }

    public void describe(String description) {
        this.description = description;
    }

    public void retitle(String title) {
        this.title = title;
    }

    public void recredit(int credits) {
        this.credits = credits;
    }

    public void activate() {
        this.status = CourseStatus.ACTIVE;
    }

    public void retire() {
        this.status = CourseStatus.RETIRED;
    }

    public void reassignDepartment(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public void replaceComponents(Collection<CourseComponent> next) {
        this.components.clear();
        this.components.addAll(next);
    }

    /** Stable catalog order: lecture, tutorial, laboratory. */
    public List<CourseComponent> orderedComponents() {
        return Arrays.stream(CourseComponent.values()).filter(components::contains).toList();
    }
}
