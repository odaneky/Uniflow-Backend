package com.university.lms.grading.domain;

/**
 * Derived at term close from cumulative GPA — a coarse, institution-wide policy. A programme- or
 * committee-specific override is a later refinement; this is deliberately the simplest rule that
 * is still correct: below the standard 2.00 graduation-GPA floor is probation, at or above is good
 * standing.
 */
public enum AcademicStanding {
    GOOD_STANDING,
    PROBATION
}
