package com.university.lms.assessment.repository;

import com.university.lms.assessment.domain.Assessment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the assessment module. */
public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    List<Assessment> findByCourseSectionId(UUID courseSectionId);

    List<Assessment> findByCourseSectionIdAndPublishedTrue(UUID courseSectionId);
}
