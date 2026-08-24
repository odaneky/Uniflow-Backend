package com.university.lms.staffing.api;

import java.util.List;
import java.util.UUID;

/**
 * The staffing module's published contract — the scope source org-scoped authorization (A5) will
 * consult once it exists. Not consumed by anything yet: replacing {@code CurrentUser.isStaff()}'s
 * blunt "any non-student role" check with something that actually restricts by appointment is
 * separate work, sequenced after this — {@link #isAppointedOver} is the check that work will call;
 * nothing calls it yet.
 */
public interface StaffAppointments {

    record Appointment(UUID orgUnitId, String orgUnitCode, String role) {}

    /** This user's appointments active today. Empty for a user with no current appointment. */
    List<Appointment> activeAppointmentsOf(UUID userId);

    /**
     * Whether this user holds an active appointment at this org unit, or at any ancestor of it — a
     * FACULTY-level appointment authorizes access to every DEPARTMENT beneath it, the way a
     * FACULTY_ADMIN role is meant to scope to the faculty they administer rather than every
     * faculty. False when the org unit does not exist, or nothing on the path to the root has an
     * active appointment for this user.
     */
    boolean isAppointedOver(UUID userId, UUID orgUnitId);

    /**
     * The org unit mirroring this academic faculty or department, if one has been linked yet —
     * empty until the registry runs the reconcile pass (or the unit was created after this wiring
     * existed). {@code sourceType} is {@code "FACULTY"} or {@code "DEPARTMENT"}.
     */
    java.util.Optional<UUID> orgUnitFor(String sourceType, UUID sourceId);
}
