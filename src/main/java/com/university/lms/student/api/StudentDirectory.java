package com.university.lms.student.api;

import java.util.Optional;
import java.util.UUID;

/**
 * The student module's published contract.
 *
 * <p>{@code eligibleToEnrol} is answered here rather than by exposing a status the caller then
 * interprets: eligibility is a student-module rule, and the enrolment module should not have to
 * know which of six academic standings happen to permit registration this year.
 */
public interface StudentDirectory {

    record StudentSummary(UUID id, UUID userId, String studentNumber, UUID programmeId, boolean eligibleToEnrol) {}

    boolean exists(UUID studentId);

    Optional<StudentSummary> findById(UUID studentId);

    boolean eligibleToEnrol(UUID studentId);

    /**
     * The student record belonging to a user account, if there is one.
     *
     * <p>This is what lets another module ask "is this enrolment the caller's own" without reading
     * the students table. Empty for staff, who have a user but no student record.
     */
    Optional<UUID> studentIdOfUser(UUID userId);
}
