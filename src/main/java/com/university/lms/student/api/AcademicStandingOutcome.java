package com.university.lms.student.api;

/**
 * The two automatically-driven outcomes of a term-close standing derivation.
 *
 * <p>Deliberately narrower than {@code student.domain.StudentStatus}: ACTIVE and PROBATION are
 * mutually reversible, so driving them automatically from a term's GPA is safe. Anything more
 * severe — suspension, dismissal — is a staff or committee decision made through the direct
 * status-change path, not this one; the escalation policy for when repeated probation should
 * become something worse is not yet defined.
 */
public enum AcademicStandingOutcome {
    ACTIVE,
    PROBATION
}
