package com.university.lms.assessment.domain;

/**
 * Whether a sitting is still going ahead.
 *
 * <p>Cancelled rather than deleted: a paper that was scheduled, published and then withdrawn is part
 * of what happened to a cohort, and the students who planned around it will ask why. Removing the
 * row erases the question along with the answer.
 */
public enum ExamSittingStatus {
    SCHEDULED,
    CANCELLED
}
