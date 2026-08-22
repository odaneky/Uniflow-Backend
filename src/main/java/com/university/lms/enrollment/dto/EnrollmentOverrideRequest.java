package com.university.lms.enrollment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Registrar override to enrol a student bypassing normal registration gates. */
public record EnrollmentOverrideRequest(
        @NotNull UUID studentId,
        @NotNull UUID courseSectionId,
        @NotBlank String reasonCode,
        String reasonDetail) {}
