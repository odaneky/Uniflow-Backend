package com.university.lms.academic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Creates a degree programme offered by a department. */
public record CreateProgrammeRequest(
        @NotNull(message = "is required") UUID departmentId,
        @NotBlank(message = "is required")
                @Size(max = 20, message = "must be at most 20 characters")
                @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "may contain only letters, digits and hyphens")
                String code,
        @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String name,
        @NotBlank(message = "is required") @Size(max = 100, message = "must be at most 100 characters")
                String degreeAward,
        @NotNull(message = "is required")
                @Positive(message = "must be greater than zero")
                @Max(value = 1000, message = "must be at most 1000")
                Integer totalCredits,
        @NotNull(message = "is required")
                @Positive(message = "must be greater than zero")
                @Max(value = 10, message = "must be at most 10")
                Integer durationYears) {}
