package com.university.lms.curriculum.api;

import java.util.Set;
import java.util.UUID;

/**
 * The curriculum module's published contract.
 *
 * <p>Enrolment asks whether a course sits on a programme; it never reads requirement blocks.
 * An unpublished curriculum (no blocks) is treated as open so registration is not blocked while
 * the registry is still authoring the degree map.
 */
public interface CurriculumCatalog {

    /**
     * Whether this programme permits enrolment in this catalog course.
     *
     * <p>True when there are no blocks, when any block is {@code FREE_ELECTIVE}, or when the
     * course is listed on any block. False when the programme has a published map that does not
     * include the course.
     */
    boolean allowsEnrolment(UUID programmeId, UUID courseId);

    /**
     * Whether the student's latest published overall result for this course is a pass.
     *
     * <p>Registered here rather than read directly off {@code grading.api.AcademicRecord} by
     * {@code enrollment}: {@code grading} already depends on {@code enrollment.api} (for the
     * credits-attempted figure in a GPA summary), and a second edge running the other way would be
     * the module graph's first cycle. {@code curriculum} already depends on {@code grading} for the
     * degree audit, and {@code enrollment} already depends on {@code curriculum} for
     * {@link #allowsEnrolment} — so this sits next to that, on an edge that already exists.
     *
     * <p>False for a course the student has never had a published overall result for, which is the
     * correct default for a prerequisite check: no evidence of passing is not evidence of passing.
     */
    boolean hasPassed(UUID studentId, UUID courseId);

    /**
     * Whether a published overall result exists for this student in this section — pass or fail,
     * either counts. Backs the rule that an enrolment cannot be marked {@code COMPLETED} ahead of
     * the grade that is supposed to be what "completed" means.
     */
    boolean hasPublishedResult(UUID studentId, UUID courseSectionId);

    /**
     * Internal course ids this student holds transfer credit for.
     *
     * <p>A transfer student may satisfy a prerequisite without ever having an internal enrolment
     * record for the required course — the credit came from another institution, not this catalog's
     * enrolment history. Callers building a "courses this student has satisfied" set from enrolment
     * history alone miss these; this is how they are added back in.
     */
    Set<UUID> transferCreditedCourseIds(UUID studentId);

    /**
     * Required-course ids an approved substitution satisfies for this student — present only when
     * the substitute course is itself passed or transfer-credited.
     *
     * <p>The same gap {@link #transferCreditedCourseIds} closes, for the same reason: a student
     * approved to take a substitute course instead of the required one has no enrolment record for
     * the required course at all, so a "courses satisfied" set built from enrolment history alone
     * misses it.
     */
    Set<UUID> substitutionSatisfiedCourseIds(UUID studentId);
}
