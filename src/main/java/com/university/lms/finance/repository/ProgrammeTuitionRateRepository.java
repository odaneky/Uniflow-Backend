package com.university.lms.finance.repository;

import com.university.lms.finance.domain.ProgrammeTuitionRate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgrammeTuitionRateRepository extends JpaRepository<ProgrammeTuitionRate, UUID> {

    /** The currently-effective override for a programme, if one exists. */
    Optional<ProgrammeTuitionRate> findByProgrammeIdAndEffectiveToIsNull(UUID programmeId);

    /** Every programme's currently-effective override, for the schedule listing. */
    List<ProgrammeTuitionRate> findAllByEffectiveToIsNull();
}
