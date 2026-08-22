package com.university.lms.enrollment.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Confirms a student's registration cart in one transaction. */
public record CheckoutEnrollmentsRequest(
        @NotNull(message = "is required") UUID studentId,
        @NotEmpty(message = "is required") List<@NotNull UUID> courseSectionIds) {}
