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

    record PublishedOverall(UUID courseSectionId, String letter, BigDecimal gradePoint, Instant recordedAt) {}

    Summary summaryOf(UUID studentId);

    List<PublishedOverall> publishedOverallOf(UUID studentId);
}
