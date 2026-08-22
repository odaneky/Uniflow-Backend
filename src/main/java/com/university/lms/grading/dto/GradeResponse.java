package com.university.lms.grading.dto;

import com.university.lms.grading.domain.Grade;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A grade as a student may see it. Carries no marker identity and no internal moderation state. */
public record GradeResponse(
        UUID id,
        UUID studentId,
        UUID courseSectionId,
        UUID assessmentId,
        BigDecimal percentage,
        String letter,
        BigDecimal gradePoint,
        Instant recordedAt) {

    public static GradeResponse from(Grade grade) {
        return new GradeResponse(
                grade.getId(),
                grade.getStudentId(),
                grade.getCourseSectionId(),
                grade.getAssessmentId(),
                grade.getPercentage(),
                grade.getLetter(),
                grade.getGradePoint(),
                grade.getCreatedAt());
    }
}
