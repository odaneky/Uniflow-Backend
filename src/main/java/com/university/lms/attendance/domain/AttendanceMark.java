package com.university.lms.attendance.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(
        name = "attendance_marks",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_attendance_marks_session_student",
                        columnNames = {"session_id", "student_id"}),
        indexes = @Index(name = "idx_attendance_marks_student", columnList = "student_id"))
@Getter
public class AttendanceMark extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AttendanceStatus status;

    @Column(name = "note", length = 500)
    private String note;

    protected AttendanceMark() {
        // for JPA
    }

    public AttendanceMark(UUID sessionId, UUID studentId, AttendanceStatus status, String note) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.status = status;
        this.note = note;
    }

    public void correctTo(AttendanceStatus status, String note) {
        this.status = status;
        this.note = note;
    }
}
