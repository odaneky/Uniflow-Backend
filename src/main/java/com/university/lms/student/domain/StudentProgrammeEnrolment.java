package com.university.lms.student.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/**
 * A student's membership in one programme, dated — the temporal record {@code students.programme_id}
 * cannot be, since that field only ever holds today's answer and is silently overwritten by the next
 * transfer.
 *
 * <p>{@code students.programme_id} remains authoritative for now; {@link
 * com.university.lms.student.service.StudentProgrammeEnrolmentService} is this table's only writer
 * and keeps the two in step by writing both in the same transaction. At most one row per student is
 * open ({@code endedOn == null}) and primary at a time — {@code uk_spe_open_primary} enforces it.
 */
@Entity
@Table(
        name = "student_programme_enrolments",
        indexes = {
            @Index(name = "idx_spe_student", columnList = "student_id"),
            @Index(name = "idx_spe_programme", columnList = "programme_id")
        })
@Getter
public class StudentProgrammeEnrolment extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;

    /** Cross-module reference into curriculum; null when the programme has no curriculum version yet. */
    @Column(name = "curriculum_version_id")
    private UUID curriculumVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private ProgrammeEnrolmentKind kind;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 30)
    private ProgrammeEnrolmentEndReason endReason;

    @Column(name = "reason", length = 500)
    private String reason;

    /** Cross-module reference into identity; who approved the change, when one was required. */
    @Column(name = "approved_by")
    private UUID approvedBy;

    protected StudentProgrammeEnrolment() {
        // for JPA
    }

    public StudentProgrammeEnrolment(
            UUID studentId,
            UUID programmeId,
            UUID curriculumVersionId,
            ProgrammeEnrolmentKind kind,
            boolean primary,
            LocalDate startedOn) {
        this.studentId = studentId;
        this.programmeId = programmeId;
        this.curriculumVersionId = curriculumVersionId;
        this.kind = kind;
        this.primary = primary;
        this.startedOn = startedOn;
    }

    public boolean isOpen() {
        return endedOn == null;
    }

    public void end(LocalDate endedOn, ProgrammeEnrolmentEndReason endReason, String reason, UUID approvedBy) {
        if (!isOpen()) {
            throw new IllegalStateException("This programme membership has already ended");
        }
        this.endedOn = endedOn;
        this.endReason = endReason;
        this.reason = reason;
        this.approvedBy = approvedBy;
    }
}
