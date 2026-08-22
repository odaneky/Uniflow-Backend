package com.university.lms.curriculum.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Graduation readiness checks for service-request fulfilment. */
public interface DegreeAudit {

    record Eligibility(
            boolean eligible,
            int creditsRequired,
            int creditsEarned,
            BigDecimal gpa,
            List<String> blockers) {}

    Eligibility eligibility(UUID studentId);
}
