package com.university.lms.academic.domain;

import com.university.lms.common.audit.BaseEntity;
import java.math.BigDecimal;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * A degree programme, e.g. {@code BSC-CS}.
 *
 * <p>Holds the credit total a student is measured against. Which courses satisfy that total lives
 * on the programme's requirement blocks in the curriculum module — core-vs-elective is a
 * property of the block, never of the course.
 */
@Entity
@Table(
        name = "programmes",
        uniqueConstraints = @UniqueConstraint(name = "uk_programmes_code", columnNames = "code"),
        indexes = @Index(name = "idx_programmes_department", columnList = "department_id"))
@Getter
public class Programme extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false, foreignKey = @ForeignKey(name = "fk_programmes_department"))
    private Department department;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** e.g. {@code BSc (Hons)}. */
    @Column(name = "degree_award", nullable = false, length = 100)
    private String degreeAward;

    @Column(name = "total_credits", nullable = false)
    private int totalCredits;

    @Column(name = "duration_years", nullable = false)
    private int durationYears;

    /** Null means inherit the institution default. */
    @Column(name = "min_semester_credits")
    private Integer minSemesterCredits;

    /** Null means inherit the institution default. */
    @Column(name = "max_semester_credits")
    private Integer maxSemesterCredits;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "programme_type", nullable = false, length = 30)
    private ProgrammeType programmeType = ProgrammeType.DEGREE;

    /** Null means use institution default (2.0). */
    @Column(name = "min_graduation_gpa", precision = 3, scale = 2)
    private BigDecimal minGraduationGpa;

    protected Programme() {
        // for JPA
    }

    public Programme(
            Department department,
            String code,
            String name,
            String degreeAward,
            int totalCredits,
            int durationYears) {
        this.department = department;
        this.code = code;
        this.name = name;
        this.degreeAward = degreeAward;
        this.totalCredits = totalCredits;
        this.durationYears = durationYears;
    }

    public void revise(String name, String degreeAward, int totalCredits, int durationYears) {
        this.name = name;
        this.degreeAward = degreeAward;
        this.totalCredits = totalCredits;
        this.durationYears = durationYears;
    }

    public void replaceCreditLoad(Integer minSemesterCredits, Integer maxSemesterCredits) {
        this.minSemesterCredits = minSemesterCredits;
        this.maxSemesterCredits = maxSemesterCredits;
    }

    public void retire() {
        this.active = false;
    }

    public void reinstate() {
        this.active = true;
    }

    public void replaceProgrammeType(ProgrammeType programmeType) {
        if (programmeType != null) {
            this.programmeType = programmeType;
        }
    }

    public void replaceMinGraduationGpa(BigDecimal minGraduationGpa) {
        this.minGraduationGpa = minGraduationGpa;
    }
}
