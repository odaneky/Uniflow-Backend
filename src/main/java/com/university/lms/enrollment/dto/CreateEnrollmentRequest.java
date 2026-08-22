package com.university.lms.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Requests registration of a student into a specific course section. */
public record CreateEnrollmentRequest(
        @NotNull(message = "is required") UUID studentId,
        @NotNull(message = "is required") UUID courseSectionId) {}
