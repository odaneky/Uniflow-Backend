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

    /**
     * Records the conferral: a {@code DegreeAward} snapshotting the programme, curriculum version,
     * GPA, credits and honours at this moment, and (through {@code StudentLifecycle.graduate}) the
     * {@code students.status} flip to {@code GRADUATED}. Callers are expected to have already
     * confirmed {@link #eligibility} themselves — this does not repeat that check — but refuses a
     * second conferral for a programme the student has already graduated from.
     */
    void recordConferral(UUID studentId, UUID actorUserId);
}
