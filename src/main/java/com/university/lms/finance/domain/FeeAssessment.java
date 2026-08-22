package com.university.lms.finance.domain;

public enum FeeAssessment {
    /** Posted once when the student first enrols in the term. */
    ONCE_PER_TERM,
    /** Posted for each enrolment this fee applies to. */
    PER_ENROLMENT,
    /** Posted per enrolment, multiplied by that section's credits. */
    PER_CREDIT
}
