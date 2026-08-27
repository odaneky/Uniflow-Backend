package com.university.lms.admissions.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record SubmitApplicationScoreRequest(
        @Min(value = 1, message = "must be at least 1") @Max(value = 5, message = "must be at most 5") int score,
        @Size(max = 2000, message = "must be at most 2000 characters") String comment) {}
