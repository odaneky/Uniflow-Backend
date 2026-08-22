package com.university.lms.admissions.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record MatriculateApplicationRequest(
        @NotNull(message = "is required")
                @Size(max = 30, message = "must be at most 30 characters")
                @Pattern(
                        regexp = "^[A-Za-z0-9/-]+$",
                        message = "may contain only letters, digits, hyphens and slashes")
                String studentNumber,
        @PastOrPresent(message = "must not be in the future") LocalDate admissionDate) {}
