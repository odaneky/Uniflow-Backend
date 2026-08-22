package com.university.lms.enrollment.api;

import java.util.List;
import java.util.UUID;

/**
 * The enrolment module's published contract.
 *
 * <p>Other modules ask whether a student may see a section's material, or who is on a roster,
 * without reading the enrolments table. Learning and grading depend on this interface rather than
 * on {@code enrollment.repository}.
 */
public interface EnrollmentDirectory {

    record SectionEnrolment(UUID enrollmentId, UUID studentId, UUID courseSectionId, String status) {}

    /**
     * Attempt number for the student's enrolment in this section, if any. Empty when they were
     * never enrolled (or only dropped without a sit).
     */
    java.util.Optional<Integer> attemptNumberOf(UUID studentId, UUID courseSectionId);

    /**
     * Whether this student may see published teaching material for the section.
     *
     * <p>True for an active or completed enrolment; false for dropped, withdrawn, pending, or
     * unknown. A student who completed the course still has a legitimate reason to revisit notes.
     */
    boolean canAccessLearning(UUID studentId, UUID courseSectionId);

    /** Seat-holding enrolments in the section — the live roster. */
    List<SectionEnrolment> rosterOf(UUID courseSectionId);

    /** How many enrolments currently occupy a seat (PENDING + ENROLLED). */
    int occupyingSeatCount(UUID courseSectionId);

    /**
     * Aligns the course module's denormalized {@code enrolled_count} with occupying enrolments.
     * Safe to call repeatedly.
     */
    void reconcileSeatCount(UUID courseSectionId);

    /** Section ids the student currently occupies or has completed. */
    List<UUID> accessibleSectionIds(UUID studentId);
}
