package com.university.lms.course.dto;

import com.university.lms.course.domain.Course;
import com.university.lms.course.domain.CourseComponent;
import com.university.lms.course.domain.CourseStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full representation of a catalog course. */
public record CourseResponse(
        UUID id,
        String courseCode,
        String title,
        String description,
        int credits,
        int level,
        UUID departmentId,
        List<CourseComponent> components,
        CourseStatus status,
        List<RequirementGroupResponse> requirements,
        Instant createdAt,
        Instant updatedAt) {

    public static CourseResponse from(Course course, List<RequirementGroupResponse> requirements) {
        return new CourseResponse(
                course.getId(),
                course.getCourseCode(),
                course.getTitle(),
                course.getDescription(),
                course.getCredits(),
                course.getLevel(),
                course.getDepartmentId(),
                course.orderedComponents(),
                course.getStatus(),
                requirements,
                course.getCreatedAt(),
                course.getUpdatedAt());
    }
}
