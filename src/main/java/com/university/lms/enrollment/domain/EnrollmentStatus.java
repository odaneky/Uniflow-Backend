package com.university.lms.enrollment.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * The lifecycle of one student's registration in one course section.
 *
 * <p>The permitted transitions are declared here rather than left to whichever service happens to
 * be setting the field. Without an explicit table, "change the status" becomes an open invitation
 * to move a COMPLETED enrolment back to ENROLLED — quietly corrupting an academic record that has
 * already been reported on.
 */
public enum EnrollmentStatus {

    /** Requested but not yet confirmed, e.g. awaiting approval for a restricted section. */
    PENDING,

    ENROLLED,

    /** On the list for a full occurrence; does not occupy a seat. */
    WAITLISTED,

    /** Cancelled within the drop window; carries no academic penalty and frees the seat. */
    DROPPED,

    /** Left after the drop window; recorded on the transcript and frees the seat. */
    WITHDRAWN,

    COMPLETED;

    private static final Map<EnrollmentStatus, Set<EnrollmentStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<EnrollmentStatus, Set<EnrollmentStatus>> transitions = new EnumMap<>(EnrollmentStatus.class);
        transitions.put(PENDING, Set.of(ENROLLED, DROPPED, WITHDRAWN));
        transitions.put(ENROLLED, Set.of(DROPPED, WITHDRAWN, COMPLETED));
        transitions.put(WAITLISTED, Set.of(ENROLLED, DROPPED));
        transitions.put(DROPPED, Set.of());
        transitions.put(WITHDRAWN, Set.of());
        transitions.put(COMPLETED, Set.of());
        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    public boolean canTransitionTo(EnrollmentStatus target) {
        return target != null && ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }

    /**
     * Whether a registration in this state is still occupying a seat in the section. Drives when
     * capacity is returned, so it must stay consistent with the reserve/release calls.
     */
    public boolean occupiesSeat() {
        return this == PENDING || this == ENROLLED;
    }

    /**
     * Whether this status still claims the student's timetable for clash detection. Waitlisted
     * rows do not take a seat, but promoting them into an overlapping occurrence would fail later.
     */
    public boolean occupiesTimetable() {
        return this == PENDING || this == ENROLLED || this == WAITLISTED;
    }
}
