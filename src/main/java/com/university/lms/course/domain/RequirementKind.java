package com.university.lms.course.domain;

/**
 * How a requirement group is satisfied.
 *
 * <p>{@code PREREQUISITE} — already completed (passed). {@code COREQUISITE} — completed or in
 * progress this term. {@code MINIMUM_LEVEL} — at least one completed course at that level or above
 * (UTech "Level 3 standing").
 */
public enum RequirementKind {
    PREREQUISITE,
    COREQUISITE,
    MINIMUM_LEVEL
}
