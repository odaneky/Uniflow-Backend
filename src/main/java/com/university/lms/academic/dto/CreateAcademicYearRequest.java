package com.university.lms.academic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Creates an academic session such as {@code 2026/2027}. */
public record CreateAcademicYearRequest(
        @NotBlank(message = "is required") @Size(max = 20, message = "must be at most 20 characters") String code,
        @NotNull(message = "is required") LocalDate startDate,
        @NotNull(message = "is required") LocalDate endDate) {}
