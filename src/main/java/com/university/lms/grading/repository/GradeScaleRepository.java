package com.university.lms.grading.repository;

import com.university.lms.grading.domain.GradeScale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the grading module. */
public interface GradeScaleRepository extends JpaRepository<GradeScale, UUID> {

    Optional<GradeScale> findByName(String name);
}
