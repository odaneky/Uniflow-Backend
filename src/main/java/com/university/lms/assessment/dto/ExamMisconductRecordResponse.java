package com.university.lms.assessment.dto;

import com.university.lms.assessment.domain.ExamMisconductRecord;
import java.time.Instant;
import java.util.UUID;

public record ExamMisconductRecordResponse(
        UUID id,
        UUID examSittingId,
        UUID studentId,
        String description,
        UUID reportedBy,
        String reportedByName,
        Instant createdAt) {

    public static ExamMisconductRecordResponse from(ExamMisconductRecord entity, String reportedByName) {
        return new ExamMisconductRecordResponse(
                entity.getId(),
                entity.getExamSittingId(),
                entity.getStudentId(),
                entity.getDescription(),
                entity.getReportedBy(),
                reportedByName,
                entity.getCreatedAt());
    }
}
