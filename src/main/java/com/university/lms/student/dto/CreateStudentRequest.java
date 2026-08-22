package com.university.lms.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Registers an existing identity account as a student.
 *
 * <p>Only structural validation lives here. Whether the user and programme actually exist, and
 * whether the student number is already taken, are decided by the service against live data.
 */
public record CreateStudentRequest(
        @NotNull(message = "is required") UUID userId,
        @NotBlank(message = "is required")
                @Size(max = 30, message = "must be at most 30 characters")
                @Pattern(
                        regexp = "^[A-Za-z0-9/-]+$",
                        message = "may contain only letters, digits, hyphens and slashes")
                String studentNumber,
        @NotNull(message = "is required") UUID programmeId,
        @NotNull(message = "is required") @PastOrPresent(message = "must not be in the future")
                LocalDate admissionDate,
        LocalDate expectedGraduationDate) {}
