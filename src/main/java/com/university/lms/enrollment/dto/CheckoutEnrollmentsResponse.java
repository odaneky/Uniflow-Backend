package com.university.lms.enrollment.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Result of confirming a registration cart; some rows may be {@code WAITLISTED}. */
public record CheckoutEnrollmentsResponse(
        UUID checkoutBatchId, Instant correctionExpiresAt, List<EnrollmentResponse> enrollments) {}
