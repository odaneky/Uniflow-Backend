package com.university.lms.learning.repository;

import com.university.lms.learning.domain.CourseContent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the learning module. */
public interface CourseContentRepository extends JpaRepository<CourseContent, UUID> {

    Optional<CourseContent> findByCourseSectionId(UUID courseSectionId);
}
