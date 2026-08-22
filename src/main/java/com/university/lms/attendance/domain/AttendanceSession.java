package com.university.lms.attendance.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(
        name = "attendance_sessions",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_attendance_sessions_section_date",
                        columnNames = {"course_section_id", "session_date"}),
        indexes = @Index(name = "idx_attendance_sessions_section", columnList = "course_section_id"))
@Getter
public class AttendanceSession extends BaseEntity {

    @Column(name = "course_section_id", nullable = false)
    private UUID courseSectionId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "topic", length = 200)
    private String topic;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    protected AttendanceSession() {
        // for JPA
    }

    public AttendanceSession(UUID courseSectionId, LocalDate sessionDate, String topic) {
        this.courseSectionId = courseSectionId;
        this.sessionDate = sessionDate;
        this.topic = topic;
    }

    public void revise(String topic) {
        this.topic = topic;
    }

    public void recordedBy(UUID userId) {
        this.recordedByUserId = userId;
    }
}
