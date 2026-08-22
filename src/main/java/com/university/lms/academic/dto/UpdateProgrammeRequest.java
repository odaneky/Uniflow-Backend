package com.university.lms.academic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Partial update of a programme; null means leave unchanged. Code is not editable. */
public record UpdateProgrammeRequest(
        @Size(max = 200, message = "must be at most 200 characters") String name,
        @Size(max = 100, message = "must be at most 100 characters") String degreeAward,
        @Positive(message = "must be greater than zero") @Max(value = 1000, message = "must be at most 1000")
                Integer totalCredits,
        @Positive(message = "must be greater than zero") @Max(value = 10, message = "must be at most 10")
                Integer durationYears) {}
