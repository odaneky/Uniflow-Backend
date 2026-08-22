package com.university.lms.course.domain;

/** Lifecycle of a specific offering of a course in a specific term. */
public enum CourseSectionStatus {

    /** Scheduled but not yet accepting registrations. */
    PLANNED,

    OPEN,

    /** Full, or registration deliberately stopped; no new enrolments. */
    CLOSED,

    CANCELLED,

    COMPLETED
}
