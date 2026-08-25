package com.university.lms.student.repository;

import com.university.lms.student.domain.AdvisingAppointment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvisingAppointmentRepository extends JpaRepository<AdvisingAppointment, UUID> {

    List<AdvisingAppointment> findByStudentIdOrderByScheduledAtDesc(UUID studentId);

    List<AdvisingAppointment> findByAdvisorUserIdOrderByScheduledAtDesc(UUID advisorUserId);
}
