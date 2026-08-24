package com.university.lms.staffing.dto;

import com.university.lms.staffing.domain.StaffAppointment;
import java.time.LocalDate;
import java.util.UUID;

public record StaffAppointmentResponse(
        UUID id, UUID userId, UUID orgUnitId, String orgUnitCode, String role, LocalDate validFrom, LocalDate validTo) {

    public static StaffAppointmentResponse from(StaffAppointment appointment) {
        return new StaffAppointmentResponse(
                appointment.getId(),
                appointment.getUserId(),
                appointment.getOrgUnit().getId(),
                appointment.getOrgUnit().getCode(),
                appointment.getRole(),
                appointment.getValidFrom(),
                appointment.getValidTo());
    }
}
