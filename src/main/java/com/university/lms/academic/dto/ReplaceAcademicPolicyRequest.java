package com.university.lms.academic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReplaceAcademicPolicyRequest(
        @NotNull @Min(1) @Max(40) Integer minSemesterCredits,
        @NotNull @Min(1) @Max(40) Integer maxSemesterCredits,
        @Min(0) @Max(168) Integer checkoutCorrectionHours) {}
