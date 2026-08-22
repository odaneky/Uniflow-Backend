package com.university.lms.grading.dto;

import java.math.BigDecimal;

/** Cumulative published results for the caller. Null GPA means nothing has been released yet. */
public record AcademicSummaryResponse(
        BigDecimal gpa, int creditsAttempted, int creditsEarned, long publishedGradeCount) {}
