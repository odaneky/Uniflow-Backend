package com.university.lms.curriculum.api;

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
}
