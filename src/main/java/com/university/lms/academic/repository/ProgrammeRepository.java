package com.university.lms.academic.repository;

import com.university.lms.academic.domain.Programme;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the academic module — cross-module reads go through {@code academic.api}. */
public interface ProgrammeRepository extends JpaRepository<Programme, UUID> {

    Optional<Programme> findByCode(String code);

    boolean existsByCode(String code);

    org.springframework.data.domain.Page<Programme> findByDepartmentId(
            UUID departmentId, org.springframework.data.domain.Pageable pageable);
}
