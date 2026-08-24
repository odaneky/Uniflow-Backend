package com.university.lms.attendance.dto;

import com.university.lms.attendance.domain.AttendanceMark;
import com.university.lms.attendance.domain.AttendanceSession;
import com.university.lms.attendance.domain.AttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AttendanceDtos {

    private AttendanceDtos() {}

    public record AttendanceSessionResponse(
            UUID id, UUID courseSectionId, LocalDate sessionDate, String topic, UUID recordedByUserId) {

        public static AttendanceSessionResponse from(AttendanceSession session) {
            return new AttendanceSessionResponse(
                    session.getId(),
                    session.getCourseSectionId(),
                    session.getSessionDate(),
                    session.getTopic(),
                    session.getRecordedByUserId());
        }
    }

    public record AttendanceMarkResponse(UUID studentId, AttendanceStatus status, String note) {

        public static AttendanceMarkResponse from(AttendanceMark mark) {
            return new AttendanceMarkResponse(mark.getStudentId(), mark.getStatus(), mark.getNote());
        }
    }

    public record AttendanceSessionDetailResponse(
            AttendanceSessionResponse session, List<AttendanceMarkResponse> marks) {}

    public record CreateAttendanceSessionRequest(
            @NotNull LocalDate sessionDate, String topic, @NotEmpty @Valid List<MarkRequest> marks) {}

    public record MarkRequest(@NotNull UUID studentId, @NotNull AttendanceStatus status, String note) {}

    /**
     * A student's attendance rate for a section, below the requested threshold.
     *
     * @param consideredSessions sessions counted toward the rate — every recorded mark except
     *     {@code EXCUSED}, since an excused absence should not count against a student
     * @param attendanceRate {@code (present + late) / consideredSessions}, as a fraction; {@code
     *     null} when the student has no considered sessions yet
     */
    public record AtRiskStudentResponse(
            UUID studentId,
            int presentCount,
            int lateCount,
            int absentCount,
            int excusedCount,
            int consideredSessions,
            Double attendanceRate) {}
}
