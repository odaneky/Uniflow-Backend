package com.university.lms.grading.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Institution-level aggregates over a closed term — distinct from {@link AcademicRecord}, which
 * answers about one student. Reads {@code term_academic_records} only, never live grades: a term
 * that has not been closed has no census, because summing mutable, still-changing history would
 * produce a number that looks authoritative and is not.
 */
public interface TermCensus {

    record PerStudentStanding(UUID studentId, BigDecimal cumulativeGpa, int credits) {}

    /**
     * Empty when the term has not been closed — no {@code term_academic_records} rows exist for it
     * yet, not zero students in it.
     */
    record Summary(
            UUID academicTermId,
            int headcount,
            BigDecimal averageTermGpa,
            int goodStandingCount,
            int probationCount,
            int creditsAttemptedTotal,
            int creditsEarnedTotal,
            List<PerStudentStanding> students) {}

    Summary summarize(UUID academicTermId);
}
