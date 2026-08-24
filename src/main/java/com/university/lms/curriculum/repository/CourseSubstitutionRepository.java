package com.university.lms.curriculum.repository;

import com.university.lms.curriculum.domain.CourseSubstitution;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the curriculum module. */
public interface CourseSubstitutionRepository extends JpaRepository<CourseSubstitution, UUID> {

    List<CourseSubstitution> findByStudentId(UUID studentId);

    Optional<CourseSubstitution> findByStudentIdAndRequiredCourseId(UUID studentId, UUID requiredCourseId);
}
