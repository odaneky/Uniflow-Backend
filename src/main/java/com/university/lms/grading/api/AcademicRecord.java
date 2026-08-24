package com.university.lms.grading.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published academic results other modules may read.
 *
 * <p>Letter and GPA stay owned by grading. Callers ask for a summary or for overall published
 * section results; they never read the grades table.
 */
public interface AcademicRecord {

    record Summary(BigDecimal gpa, int creditsAttempted, int creditsEarned, long publishedGradeCount) {}

    /** @param pass whether {@code letter} is a passing result — grading owns that interpretation;
     *      callers must never re-derive it from the raw letter. */
    record PublishedOverall(
            UUID courseSectionId, String letter, BigDecimal gradePoint, boolean pass, Instant recordedAt) {}

    Summary summaryOf(UUID studentId);

    List<PublishedOverall> publishedOverallOf(UUID studentId);
}
