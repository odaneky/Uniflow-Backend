package com.university.lms.assessment.dto;

import com.university.lms.assessment.domain.ExamResitCandidate;
import java.time.Instant;
import java.util.UUID;

public record ExamResitCandidateResponse(UUID studentId, String studentNumber, Instant addedAt) {

    public static ExamResitCandidateResponse from(ExamResitCandidate candidate, String studentNumber) {
        return new ExamResitCandidateResponse(candidate.getStudentId(), studentNumber, candidate.getAddedAt());
    }
}
