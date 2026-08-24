package com.university.lms.student.dto;

import com.university.lms.student.domain.AdvisingNote;
import java.time.Instant;
import java.util.UUID;

public record AdvisingNoteResponse(
        UUID id, UUID studentId, UUID advisorUserId, String advisorName, String note, Instant createdAt) {

    public static AdvisingNoteResponse from(AdvisingNote entity, String advisorName) {
        return new AdvisingNoteResponse(
                entity.getId(), entity.getStudentId(), entity.getAdvisorUserId(), advisorName, entity.getNote(), entity.getCreatedAt());
    }
}
