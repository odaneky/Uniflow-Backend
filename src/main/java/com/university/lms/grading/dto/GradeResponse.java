package com.university.lms.grading.dto;

import com.university.lms.grading.domain.Grade;
import com.university.lms.grading.domain.GradeResult;
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
        Instant recordedAt,
        String courseCode,
        String courseTitle,
        Integer credits,
        String academicYearCode,
        String termName,
        Integer level,
        Integer attemptNumber,
        GradeResult result) {

    public static GradeResponse from(Grade grade) {
        return from(grade, null, null, null, null, null, null, null);
    }

    public static GradeResponse from(Grade grade, String courseCode, String courseTitle, Integer credits) {
        return from(grade, courseCode, courseTitle, credits, null, null, null, null);
    }

    public static GradeResponse from(
            Grade grade,
            String courseCode,
            String courseTitle,
            Integer credits,
            String academicYearCode,
            String termName,
            Integer level,
            Integer attemptNumber) {
        GradeResult result = grade.getAssessmentId() == null ? GradeResult.fromLetter(grade.getLetter()) : null;
        return new GradeResponse(
                grade.getId(),
                grade.getStudentId(),
                grade.getCourseSectionId(),
                grade.getAssessmentId(),
                grade.getPercentage(),
                grade.getLetter(),
                grade.getGradePoint(),
                grade.getCreatedAt(),
                courseCode,
                courseTitle,
                credits,
                academicYearCode,
                termName,
                level,
                attemptNumber,
                result);
    }
}
