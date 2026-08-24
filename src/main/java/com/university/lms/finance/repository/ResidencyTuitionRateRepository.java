package com.university.lms.finance.repository;

import com.university.lms.finance.domain.ResidencyTuitionRate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResidencyTuitionRateRepository extends JpaRepository<ResidencyTuitionRate, UUID> {

    Optional<ResidencyTuitionRate> findByResidencyClassification(String residencyClassification);

    void deleteByResidencyClassification(String residencyClassification);
}
