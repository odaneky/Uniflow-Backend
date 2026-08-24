package com.university.lms.student.repository;

import com.university.lms.student.domain.AdvisorOfficeHours;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvisorOfficeHoursRepository extends JpaRepository<AdvisorOfficeHours, UUID> {

    Optional<AdvisorOfficeHours> findByAdvisorUserId(UUID advisorUserId);
}
