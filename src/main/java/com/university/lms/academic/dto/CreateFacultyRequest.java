package com.university.lms.academic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Creates a faculty — the top of the Faculty → Department → Programme hierarchy. */
public record CreateFacultyRequest(
        @NotBlank(message = "is required")
                @Size(max = 20, message = "must be at most 20 characters")
                @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "may contain only letters, digits and hyphens")
                String code,
        @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String name,
        /** Optional; must reference an existing user when supplied. */
        UUID deanUserId) {}
