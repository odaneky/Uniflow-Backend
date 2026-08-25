package com.university.lms.student.api;

import com.university.lms.student.dto.UpdateOwnProfileRequest;
import java.util.UUID;

/** Student standing changes triggered by approved registry requests. */
public interface StudentLifecycle {

    void graduate(UUID studentId, UUID actorUserId);

    void applyContactCorrection(UUID studentId, UpdateOwnProfileRequest contact);

    void beginLeave(UUID studentId, UUID actorUserId);

    void readmit(UUID studentId, UUID actorUserId);

    /** Closes the student's open primary programme membership and opens a new one, reviewed. */
    void transferProgramme(UUID studentId, UUID newProgrammeId, String reason, UUID actorUserId);

    /**
     * Applies a term-close-derived standing outcome, if the student's current status is one this is
     * safe to drive automatically. A no-op for a student not currently ACTIVE or PROBATION — a
     * student who is, say, ON_LEAVE or WITHDRAWN is not "in academic standing" in the sense this
     * applies to, and forcing a transition for them would be wrong rather than merely unnecessary.
     */
    void applyAcademicStanding(UUID studentId, AcademicStandingOutcome outcome, String reason);
}
