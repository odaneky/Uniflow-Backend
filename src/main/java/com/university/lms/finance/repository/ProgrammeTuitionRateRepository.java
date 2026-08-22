package com.university.lms.finance.repository;

import com.university.lms.finance.domain.ProgrammeTuitionRate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgrammeTuitionRateRepository extends JpaRepository<ProgrammeTuitionRate, UUID> {

    Optional<ProgrammeTuitionRate> findByProgrammeId(UUID programmeId);

    void deleteByProgrammeId(UUID programmeId);
}
