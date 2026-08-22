package com.university.lms.assessment.dto;

import com.university.lms.assessment.domain.AssessmentType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Defines assessed work for a course section. */
public record CreateAssessmentRequest(
        @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String title,
        @Size(max = 4000, message = "must be at most 4000 characters") String instructions,
        @NotNull(message = "is required") AssessmentType assessmentType,
        @NotNull(message = "is required") @DecimalMin(value = "0.01", message = "must be greater than zero")
                BigDecimal maxScore,
        @NotNull(message = "is required")
                @DecimalMin(value = "0.00", message = "must be at least 0")
                @DecimalMax(value = "100.00", message = "must be at most 100")
                BigDecimal weightPercent,
        Instant dueAt,
        Boolean published,
        Integer durationMinutes,
        @DecimalMin(value = "0.00", message = "must be at least 0")
                @DecimalMax(value = "100.00", message = "must be at most 100")
                BigDecimal passMarkPercent) {}
