package com.university.lms.enrollment.api;

import java.util.UUID;

/** Cross-module enrolment mutations invoked by workflow fulfilment (e.g. approved withdrawals). */
public interface EnrollmentActions {

    /** Withdraws an enrolment on behalf of a staff/system actor. */
    void withdraw(UUID enrollmentId, UUID actorUserId);

    /** Whether the enrolment belongs to the student and may be withdrawn. */
    boolean canWithdraw(UUID enrollmentId, UUID studentId);

    /** Late-add enrolment after an approved petition. */
    void lateAdd(UUID studentId, UUID courseSectionId, UUID actorUserId);
}
