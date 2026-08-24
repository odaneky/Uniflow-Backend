package com.university.lms.staffing.repository;

import com.university.lms.staffing.domain.StaffAppointment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the staffing module. */
public interface StaffAppointmentRepository extends JpaRepository<StaffAppointment, UUID> {

    List<StaffAppointment> findByUserId(UUID userId);

    List<StaffAppointment> findByOrgUnitId(UUID orgUnitId);
}
