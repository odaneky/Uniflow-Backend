package com.university.lms.curriculum.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One registrar-approved course substitution, resolved for a programme's degree-audit screen.
 *
 * <p>The {@code course_substitutions} row itself carries only ids; the student number and both
 * course code/title pairs are looked up when this list is assembled. A course that has since been
 * removed from the catalog leaves its code/title fields null rather than dropping the row — the
 * substitution still happened.
 */
public record CourseSubstitutionResponse(
        UUID studentId,
        String studentNumber,
        UUID requiredCourseId,
        String requiredCourseCode,
        String requiredCourseTitle,
        UUID substituteCourseId,
        String substituteCourseCode,
        String substituteCourseTitle,
        UUID serviceRequestId,
        UUID approvedBy,
        Instant approvedAt) {}
