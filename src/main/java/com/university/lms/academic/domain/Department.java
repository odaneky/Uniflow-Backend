package com.university.lms.academic.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;

/** A department within a {@link Faculty}; owns courses and programmes. */
@Entity
@Table(
        name = "departments",
        uniqueConstraints = @UniqueConstraint(name = "uk_departments_code", columnNames = "code"),
        indexes = @Index(name = "idx_departments_faculty", columnList = "faculty_id"))
@Getter
public class Department extends BaseEntity {

    /** Same module, so a real association is appropriate here. Lazy: most reads do not need it. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false, foreignKey = @ForeignKey(name = "fk_departments_faculty"))
    private Faculty faculty;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "head_user_id")
    private UUID headUserId;

    protected Department() {
        // for JPA
    }

    public Department(Faculty faculty, String code, String name) {
        this.faculty = faculty;
        this.code = code;
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void assignHead(UUID headUserId) {
        this.headUserId = headUserId;
    }
}
