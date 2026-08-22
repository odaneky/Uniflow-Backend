package com.university.lms.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Adds a teaching unit to a section's content. */
public record CreateModuleRequest(
        @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String title,
        Integer position,
        Boolean published) {}
