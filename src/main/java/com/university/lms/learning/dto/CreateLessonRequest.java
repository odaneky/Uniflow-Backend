package com.university.lms.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Adds a lesson under a learning module. */
public record CreateLessonRequest(
        @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String title,
        @Size(max = 2000, message = "must be at most 2000 characters") String summary,
        Integer position) {}
