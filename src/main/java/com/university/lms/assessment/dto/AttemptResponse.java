package com.university.lms.assessment.dto;

import com.university.lms.assessment.domain.AssessmentAttempt;
import com.university.lms.assessment.domain.AttemptStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One attempt at an assessment. Never includes a storage key. */
public record AttemptResponse(
        UUID id,
        UUID assessmentId,
        UUID studentId,
        String studentNumber,
        String fullName,
        int attemptNumber,
        AttemptStatus status,
        Instant submittedAt,
        BigDecimal rawScore,
        UUID documentId,
        String fileName,
        Long sizeBytes) {

    public static AttemptResponse from(
            AssessmentAttempt attempt, String studentNumber, String fullName, String fileName, Long sizeBytes) {
        return new AttemptResponse(
                attempt.getId(),
                attempt.getAssessment().getId(),
                attempt.getStudentId(),
                studentNumber,
                fullName,
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getSubmittedAt(),
                attempt.getRawScore(),
                attempt.getDocumentId(),
                fileName,
                sizeBytes);
    }
}
