package com.university.lms.student.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Academic standing, distinct from the login account's {@code UserStatus}: a graduated student
 * keeps an active account for transcript access long after they stop being enrolled.
 *
 * <p>The permitted transitions are declared here rather than left to whichever service happens to
 * be setting the field — modelled directly on {@code enrollment.domain.EnrollmentStatus}, which
 * already does this well. Without an explicit table, "change the status" is an open invitation to
 * move a DISMISSED record back to ACTIVE, quietly reversing a decision with no trace of how.
 *
 * <p>Enforced by {@code StudentService.applyStatusChange} on the direct {@code PATCH
 * /api/v1/students/{id}} path. {@code DefaultStudentLifecycle}'s workflow-driven transitions
 * (graduation, leave of absence, readmission) keep their own narrower, already-tested guards —
 * unifying those onto this table is separate work, not done here.
 */
public enum StudentStatus {

    APPLICANT,

    /** Offer accepted; has not yet started classes. */
    ADMITTED,

    /** Admitted but has pushed their start to a later term. */
    DEFERRED,

    ACTIVE,

    /** Approved temporary absence; the record is preserved and may return to {@link #ACTIVE}. */
    ON_LEAVE,

    /** Active, but under academic warning — typically for falling below a GPA or SAP threshold. */
    PROBATION,

    SUSPENDED,

    /** Permanently removed for academic or conduct reasons; unlike {@link #SUSPENDED}, terminal. */
    DISMISSED,

    WITHDRAWN,

    /**
     * Finished the requirements of a non-degree programme (e.g. a certificate). Distinct from
     * {@link #GRADUATED}, which is a formal degree conferral.
     */
    COMPLETED,

    GRADUATED,

    /** The long-term resting state after {@link #GRADUATED} or {@link #COMPLETED}. */
    ALUMNI;

    private static final Map<StudentStatus, Set<StudentStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<StudentStatus, Set<StudentStatus>> transitions = new EnumMap<>(StudentStatus.class);
        transitions.put(APPLICANT, Set.of(ADMITTED, DEFERRED, WITHDRAWN));
        transitions.put(ADMITTED, Set.of(ACTIVE, DEFERRED, WITHDRAWN));
        transitions.put(DEFERRED, Set.of(ADMITTED, WITHDRAWN));
        transitions.put(ACTIVE, Set.of(ON_LEAVE, PROBATION, SUSPENDED, WITHDRAWN, COMPLETED, GRADUATED));
        transitions.put(ON_LEAVE, Set.of(ACTIVE, WITHDRAWN, COMPLETED, GRADUATED));
        transitions.put(PROBATION, Set.of(ACTIVE, SUSPENDED, DISMISSED, WITHDRAWN, COMPLETED, GRADUATED));
        transitions.put(SUSPENDED, Set.of(ACTIVE, DISMISSED, WITHDRAWN));
        transitions.put(DISMISSED, Set.of());
        // Readmission after withdrawal is a real, existing path — DefaultStudentLifecycle.readmit.
        transitions.put(WITHDRAWN, Set.of(ACTIVE));
        transitions.put(COMPLETED, Set.of(ALUMNI));
        transitions.put(GRADUATED, Set.of(ALUMNI));
        transitions.put(ALUMNI, Set.of());
        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    public boolean canTransitionTo(StudentStatus target) {
        return target != null && this != target && ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }
}
