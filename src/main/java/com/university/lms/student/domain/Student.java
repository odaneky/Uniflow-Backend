package com.university.lms.student.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/**
 * A person's enrolment record with the university.
 *
 * <p>{@code userId} and {@code programmeId} are plain identifiers, not JPA associations, because
 * they point into other modules (identity and academic). The database still enforces referential
 * integrity through foreign keys; what is avoided is a compile-time dependency that would make
 * those modules impossible to separate later. See {@code docs/architecture.md}.
 */
@Entity
@Table(
        name = "students",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_students_student_number", columnNames = "student_number"),
            @UniqueConstraint(name = "uk_students_user", columnNames = "user_id"),
            @UniqueConstraint(name = "uk_students_profile", columnNames = "profile_id")
        },
        indexes = {
            @Index(name = "idx_students_programme", columnList = "programme_id"),
            @Index(name = "idx_students_status", columnList = "status"),
            @Index(name = "idx_students_advisor", columnList = "advisor_user_id")
        })
@Getter
public class Student extends BaseEntity {

    /** The login account this record belongs to. Cross-module reference into identity. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "student_number", nullable = false, length = 30)
    private String studentNumber;

    /** Cross-module reference into academic. */
    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StudentStatus status = StudentStatus.ACTIVE;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    @Column(name = "expected_graduation_date")
    private LocalDate expectedGraduationDate;

    /** Cross-module reference into identity — the staff member advising this student. */
    @Column(name = "advisor_user_id")
    private UUID advisorUserId;

    @Column(name = "advisor_office_hours", length = 200)
    private String advisorOfficeHours;

    /**
     * Owned from this side so the association can genuinely be lazy — the inverse side of a
     * {@code @OneToOne} cannot be, and would load personal data on every student read.
     */
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_students_profile"))
    private StudentProfile profile;

    /**
     * Student records are updated from several directions at once — the registry editing standing,
     * an advisor changing programme, the student updating contact details — so a lost update here
     * is a realistic risk rather than a theoretical one.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Student() {
        // for JPA
    }

    public Student(UUID userId, String studentNumber, UUID programmeId, LocalDate admissionDate) {
        this.userId = userId;
        this.studentNumber = studentNumber;
        this.programmeId = programmeId;
        this.admissionDate = admissionDate;
        this.profile = StudentProfile.empty();
    }

    /** Only an active student may enrol; every other standing is a hard stop. */
    public boolean canEnrol() {
        return status == StudentStatus.ACTIVE;
    }

    public void transferToProgramme(UUID programmeId) {
        this.programmeId = programmeId;
    }

    public void changeStatus(StudentStatus status) {
        this.status = status;
    }

    public void expectGraduationOn(LocalDate expectedGraduationDate) {
        this.expectedGraduationDate = expectedGraduationDate;
    }

    public void assignAdvisor(UUID advisorUserId, String officeHours) {
        this.advisorUserId = advisorUserId;
        this.advisorOfficeHours = officeHours;
    }

    public void clearAdvisor() {
        this.advisorUserId = null;
        this.advisorOfficeHours = null;
    }

    public void setAdvisorOfficeHours(String officeHours) {
        this.advisorOfficeHours = officeHours;
    }
}
