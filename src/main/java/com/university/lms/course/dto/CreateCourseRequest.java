package com.university.lms.course.dto;

import com.university.lms.course.domain.CourseComponent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/** Creates a catalog course definition. Sections are added separately. */
public record CreateCourseRequest(
        @NotBlank(message = "is required")
                @Size(max = 20, message = "must be at most 20 characters")
                @Pattern(
                        regexp = "^[A-Z]{2,6}[0-9]{3,5}$",
                        message = "must look like COMP3101 — 2-6 letters followed by 3-5 digits")
                String courseCode,
        @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String title,
        @Size(max = 4000, message = "must be at most 4000 characters") String description,
        @NotNull(message = "is required")
                @Positive(message = "must be greater than zero")
                @Max(value = 60, message = "must be at most 60")
                Integer credits,
        @NotNull(message = "is required")
                @Min(value = 1, message = "must be at least 1")
                @Max(value = 9, message = "must be at most 9")
                Integer level,
        @NotNull(message = "is required") UUID departmentId,
        @NotEmpty(message = "must include at least one component") Set<CourseComponent> components) {}
