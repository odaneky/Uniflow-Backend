package com.university.lms.course.repository;

import com.university.lms.course.domain.Building;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingRepository extends JpaRepository<Building, UUID> {

    boolean existsByCode(String code);

    Optional<Building> findByCode(String code);
}
