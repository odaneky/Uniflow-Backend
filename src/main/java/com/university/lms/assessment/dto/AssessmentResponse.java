package com.university.lms.assessment.dto;

import com.university.lms.assessment.domain.Assessment;
import com.university.lms.assessment.domain.AssessmentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AssessmentResponse(
        UUID id,
        UUID courseSectionId,
        String title,
        String instructions,
        AssessmentType assessmentType,
        BigDecimal maxScore,
        BigDecimal weightPercent,
        Instant dueAt,
        boolean published,
        boolean examBlocked,
        Integer durationMinutes,
        BigDecimal passMarkPercent,
        boolean showCorrectAnswers) {

    public static AssessmentResponse from(Assessment assessment) {
        return from(assessment, false);
    }

    public static AssessmentResponse from(Assessment assessment, boolean examBlocked) {
        return new AssessmentResponse(
                assessment.getId(),
                assessment.getCourseSectionId(),
                assessment.getTitle(),
                assessment.getInstructions(),
                assessment.getAssessmentType(),
                assessment.getMaxScore(),
                assessment.getWeightPercent(),
                assessment.getDueAt(),
                assessment.isPublished(),
                examBlocked && isSitIn(assessment.getAssessmentType()),
                assessment.getDurationMinutes(),
                assessment.getPassMarkPercent(),
                assessment.isShowCorrectAnswers());
    }

    private static boolean isSitIn(AssessmentType type) {
        return type == AssessmentType.EXAM || type == AssessmentType.QUIZ;
    }
}
