package com.university.lms.grading.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Awards a mark. Letter and grade-point are taken from the scale, never from the client, so a
 * caller cannot invent an A+ for 40%.
 */
public record CreateGradeRequest(
        @NotNull(message = "is required") UUID studentId,
        @NotNull(message = "is required") UUID courseSectionId,
        UUID assessmentId,
        UUID gradeScaleId,
        @NotNull(message = "is required")
                @DecimalMin(value = "0.00", message = "must be at least 0")
                @DecimalMax(value = "100.00", message = "must be at most 100")
                BigDecimal percentage,
        Boolean publish) {}
