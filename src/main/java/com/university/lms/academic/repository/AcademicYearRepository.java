package com.university.lms.academic.repository;

import com.university.lms.academic.domain.AcademicYear;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the academic module — cross-module reads go through {@code academic.api}. */
public interface AcademicYearRepository extends JpaRepository<AcademicYear, UUID> {

    Optional<AcademicYear> findByCode(String code);

    boolean existsByCode(String code);
}
