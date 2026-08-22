package com.university.lms.course.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** Partial update of an occurrence; null means leave unchanged. */
public record UpdateSectionRequest(
        @Positive(message = "must be greater than zero") @Max(value = 2000, message = "must be at most 2000")
                Integer capacity,
        @Valid List<SectionComponentRequest> components) {}
