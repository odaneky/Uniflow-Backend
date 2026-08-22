package com.university.lms.curriculum.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(
        name = "transfer_credits",
        indexes = @Index(name = "idx_transfer_credits_student", columnList = "student_id"))
@Getter
public class TransferCredit extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "external_institution", nullable = false, length = 200)
    private String externalInstitution;

    @Column(name = "external_course_code", nullable = false, length = 50)
    private String externalCourseCode;

    @Column(name = "external_course_title", length = 200)
    private String externalCourseTitle;

    @Column(name = "internal_course_id")
    private UUID internalCourseId;

    @Column(name = "credits_awarded", nullable = false)
    private int creditsAwarded;

    @Column(name = "awarded_at", nullable = false)
    private LocalDate awardedAt;

    @Column(name = "note", length = 500)
    private String note;

    protected TransferCredit() {
        // for JPA
    }

    public TransferCredit(
            UUID studentId,
            String externalInstitution,
            String externalCourseCode,
            String externalCourseTitle,
            UUID internalCourseId,
            int creditsAwarded,
            LocalDate awardedAt,
            String note) {
        this.studentId = studentId;
        this.externalInstitution = externalInstitution;
        this.externalCourseCode = externalCourseCode;
        this.externalCourseTitle = externalCourseTitle;
        this.internalCourseId = internalCourseId;
        this.creditsAwarded = creditsAwarded;
        this.awardedAt = awardedAt;
        this.note = note;
    }
}
