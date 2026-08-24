package com.university.lms.staffing.api;

import java.util.List;
import java.util.UUID;

/**
 * The staffing module's published contract — the scope source org-scoped authorization (A5) will
 * consult once it exists. Not consumed by anything yet: replacing {@code CurrentUser.isStaff()}'s
 * blunt "any non-student role" check with something that actually restricts by appointment is
 * separate work, sequenced after this.
 */
public interface StaffAppointments {

    record Appointment(UUID orgUnitId, String orgUnitCode, String role) {}

    /** This user's appointments active today. Empty for a user with no current appointment. */
    List<Appointment> activeAppointmentsOf(UUID userId);
}
