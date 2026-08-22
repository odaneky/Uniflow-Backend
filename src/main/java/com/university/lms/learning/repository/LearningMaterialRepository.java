package com.university.lms.learning.repository;

import com.university.lms.learning.domain.LearningMaterial;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the learning module. */
public interface LearningMaterialRepository extends JpaRepository<LearningMaterial, UUID> {

    List<LearningMaterial> findByLessonIdOrderByPositionAsc(UUID lessonId);
}
