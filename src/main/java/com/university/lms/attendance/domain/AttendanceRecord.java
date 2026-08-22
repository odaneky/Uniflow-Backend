package com.university.lms.attendance.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/**
 * Attendance for one student at one session of a course section.
 *
 * <p>The (section, student, date) triple is unique so that re-submitting a register corrects the
 * existing row rather than silently accumulating duplicates — which would quietly break any
 * attendance percentage computed from it.
 */
@Entity
@Table(
        name = "attendance_records",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_attendance_section_student_date",
                        columnNames = {"course_section_id", "student_id", "session_date"}),
        indexes = {
            @Index(name = "idx_attendance_student", columnList = "student_id"),
            @Index(name = "idx_attendance_section_date", columnList = "course_section_id,session_date")
        })
@Getter
public class AttendanceRecord extends BaseEntity {

    /** Cross-module reference into course. */
    @Column(name = "course_section_id", nullable = false)
    private UUID courseSectionId;

    /** Cross-module reference into student. */
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AttendanceStatus status;

    /** Cross-module reference into identity — the staff member who took the register. */
    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @Column(name = "note", length = 500)
    private String note;

    protected AttendanceRecord() {
        // for JPA
    }

    public AttendanceRecord(UUID courseSectionId, UUID studentId, LocalDate sessionDate, AttendanceStatus status) {
        this.courseSectionId = courseSectionId;
        this.studentId = studentId;
        this.sessionDate = sessionDate;
        this.status = status;
    }

    public void correctTo(AttendanceStatus status, String note) {
        this.status = status;
        this.note = note;
    }

    public void recordedBy(UUID userId) {
        this.recordedByUserId = userId;
    }
}
