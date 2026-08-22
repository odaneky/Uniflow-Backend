package com.university.lms.course.dto;

import com.university.lms.course.domain.CourseComponent;
import com.university.lms.course.domain.CourseStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Partial update of a course definition; null means "leave unchanged". */
public record UpdateCourseRequest(
        @Size(max = 200, message = "must be at most 200 characters") String title,
        @Size(max = 4000, message = "must be at most 4000 characters") String description,
        @Positive(message = "must be greater than zero") @Max(value = 60, message = "must be at most 60")
                Integer credits,
        Set<CourseComponent> components,
        CourseStatus status) {}
