package com.university.lms.grading.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * A named marking scheme, e.g. "Undergraduate 2026".
 *
 * <p>Versioned by row rather than edited in place: a scale that has already been used to award
 * grades must keep meaning exactly what it meant then, so a change of policy creates a new scale
 * and leaves historical results interpretable.
 */
@Entity
@Table(name = "grade_scales", uniqueConstraints = @UniqueConstraint(name = "uk_grade_scales_name", columnNames = "name"))
@Getter
public class GradeScale extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected GradeScale() {
        // for JPA
    }

    public GradeScale(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void retire() {
        this.active = false;
    }
}
