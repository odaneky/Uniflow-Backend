package com.university.lms.learning.repository;

import com.university.lms.learning.domain.LearningModule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the learning module. */
public interface LearningModuleRepository extends JpaRepository<LearningModule, UUID> {

    List<LearningModule> findByCourseContentIdOrderByPositionAsc(UUID courseContentId);

    List<LearningModule> findByCourseContentIdAndPublishedTrueOrderByPositionAsc(UUID courseContentId);
}
