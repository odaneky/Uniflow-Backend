package com.university.lms.course.domain;

/** Lifecycle of a course definition in the catalog. */
public enum CourseStatus {

    /** Being authored; not yet offerable. */
    DRAFT,

    /** Approved and available to be offered as sections. */
    ACTIVE,

    /**
     * Withdrawn from the catalog. Existing sections and historical results are untouched — a
     * retired course must remain readable for transcripts.
     */
    RETIRED
}
