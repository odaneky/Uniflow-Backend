package com.university.lms.staffing.repository;

import com.university.lms.staffing.domain.Employee;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the staffing module. */
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
