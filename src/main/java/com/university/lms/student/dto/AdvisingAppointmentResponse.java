package com.university.lms.student.dto;

import com.university.lms.student.domain.AdvisingAppointment;
import java.time.Instant;
import java.util.UUID;

public record AdvisingAppointmentResponse(
        UUID id,
        UUID studentId,
        UUID advisorUserId,
        String advisorName,
        Instant scheduledAt,
        int durationMinutes,
        String note,
        boolean cancelled,
        String cancelledReason,
        Instant createdAt) {

    public static AdvisingAppointmentResponse from(AdvisingAppointment entity, String advisorName) {
        return new AdvisingAppointmentResponse(
                entity.getId(),
                entity.getStudentId(),
                entity.getAdvisorUserId(),
                advisorName,
                entity.getScheduledAt(),
                entity.getDurationMinutes(),
                entity.getNote(),
                entity.isCancelled(),
                entity.getCancelledReason(),
                entity.getCreatedAt());
    }
}
