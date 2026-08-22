package com.university.lms.course.dto;

import com.university.lms.course.domain.CourseComponent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** One component inside an occurrence set. */
public record SectionComponentRequest(
        @NotNull(message = "is required") CourseComponent component,
        @NotNull(message = "is required")
                @Positive(message = "must be greater than zero")
                @Max(value = 2000, message = "must be at most 2000")
                Integer capacity,
        UUID lecturerUserId) {}
