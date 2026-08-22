package com.university.lms.course.repository;

import com.university.lms.course.domain.CourseRequirementGroup;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the course module. */
public interface CourseRequirementGroupRepository extends JpaRepository<CourseRequirementGroup, UUID> {

    List<CourseRequirementGroup> findByCourseIdOrderByPositionAsc(UUID courseId);

    void deleteByCourseId(UUID courseId);
}
