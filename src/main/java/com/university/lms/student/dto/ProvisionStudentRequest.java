package com.university.lms.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Academic provisioning: attaches a university academic record to an identity that already exists.
 *
 * <p>Keyed by <b>student number</b> rather than by a local user id, because at this point there may
 * be no local row at all — the student has been created upstream and provisioned into the identity
 * provider, but has never signed in here. Requiring a local id would mean nobody could be given an
 * academic record until they had already logged in, which inverts the real sequence: a student is
 * admitted, registered and enrolled long before they first open the portal.
 */
public record ProvisionStudentRequest(
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
