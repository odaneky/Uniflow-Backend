package com.university.lms.curriculum.domain;

/**
 * How a requirement block counts toward a programme.
 *
 * <p>This is the classification that must not live on {@code Course}: the same course is core to
 * one programme and elective to another.
 */
public enum RequirementKind {
    CORE,
    ELECTIVE,
    GENERAL_EDUCATION,
    FREE_ELECTIVE
}
