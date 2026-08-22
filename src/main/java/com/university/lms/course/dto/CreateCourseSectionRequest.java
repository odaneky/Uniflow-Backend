package com.university.lms.course.dto;

import com.university.lms.course.domain.CourseComponent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Schedules one occurrence set of a course in a given term. */
public record CreateCourseSectionRequest(
        @NotNull(message = "is required") UUID academicTermId,
        @Size(max = 20, message = "must be at most 20 characters") String sectionCode,
        CourseComponent component,
        @NotNull(message = "is required")
                @Positive(message = "must be greater than zero")
                @Max(value = 2000, message = "must be at most 2000")
                Integer capacity,
        UUID lecturerUserId,
        @Valid List<SectionComponentRequest> components) {}
