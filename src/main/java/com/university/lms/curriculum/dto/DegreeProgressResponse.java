package com.university.lms.curriculum.dto;

import com.university.lms.curriculum.domain.RequirementKind;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DegreeProgressResponse(
        UUID programmeId,
        String programmeCode,
        String programmeName,
        String degreeAward,
        int creditsRequired,
        int creditsEarned,
        int creditsAttempted,
        BigDecimal gpa,
        List<RequirementProgressResponse> blocks,
        List<CurriculumCourseResponse> remaining) {

    public record RequirementProgressResponse(
            UUID id,
            String name,
            RequirementKind kind,
            int creditsRequired,
            int creditsEarned,
            List<CurriculumCourseResponse> remaining) {}

    public record CurriculumCourseResponse(UUID courseId, String courseCode, String title, int credits) {}
}
