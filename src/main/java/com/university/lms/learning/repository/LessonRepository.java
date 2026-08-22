package com.university.lms.learning.repository;

import com.university.lms.learning.domain.Lesson;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the learning module. */
public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    List<Lesson> findByLearningModuleIdOrderByPositionAsc(UUID learningModuleId);
}
