package com.university.lms.assessment.dto;

import com.university.lms.assessment.domain.ExamInvigilator;
import java.time.Instant;
import java.util.UUID;

public record ExamInvigilatorResponse(UUID userId, String userName, Instant assignedAt) {

    public static ExamInvigilatorResponse from(ExamInvigilator invigilator, String userName) {
        return new ExamInvigilatorResponse(invigilator.getUserId(), userName, invigilator.getAssignedAt());
    }
}
