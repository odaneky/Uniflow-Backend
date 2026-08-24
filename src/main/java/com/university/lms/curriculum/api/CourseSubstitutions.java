package com.university.lms.curriculum.api;

import java.util.UUID;

/**
 * Recording an approved course substitution — the write half of {@code course_substitutions}.
 * Separate from {@link CurriculumCatalog} the way {@code financialaid.api.HoldActions} is separate
 * from a read-shaped contract: this is invoked by the request-fulfilment workflow when a
 * {@code COURSE_SUBSTITUTION} request is approved, not queried by it.
 */
public interface CourseSubstitutions {

    /**
     * Approves {@code substituteCourseId} as satisfying {@code requiredCourseId} for this student.
     * Idempotent per (student, required course): approving the same pair again replaces the
     * existing substitution rather than erroring, since a registrar correcting a mistaken
     * substitution is a normal thing to do.
     */
    void record(UUID studentId, UUID requiredCourseId, UUID substituteCourseId, UUID serviceRequestId, UUID approvedBy);
}
