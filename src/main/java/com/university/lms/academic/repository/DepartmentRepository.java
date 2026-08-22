package com.university.lms.academic.repository;

import com.university.lms.academic.domain.Department;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the academic module — cross-module reads go through {@code academic.api}. */
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    Optional<Department> findByCode(String code);

    boolean existsByCode(String code);

    List<Department> findByFacultyId(UUID facultyId);

    org.springframework.data.domain.Page<Department> findByFacultyId(
            UUID facultyId, org.springframework.data.domain.Pageable pageable);
}
