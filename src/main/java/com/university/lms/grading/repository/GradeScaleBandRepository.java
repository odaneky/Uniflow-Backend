package com.university.lms.grading.repository;

import com.university.lms.grading.domain.GradeScaleBand;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the grading module. */
public interface GradeScaleBandRepository extends JpaRepository<GradeScaleBand, UUID> {

    List<GradeScaleBand> findByGradeScaleId(UUID gradeScaleId);
}
