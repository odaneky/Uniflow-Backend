package com.university.lms.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Administrative request to provision an identity.
 *
 * <p><b>There is no password field.</b> UniFlow does not accept, generate, transport or store one.
 * The account is created at the identity provider requiring a credential reset, and the initial
 * credential reaches the person through the university's onboarding process.
 *
 * @param username the login identifier. For a student this is their institutional student ID.
 * @param studentNumber optional; when present it is written to the identity provider as an
 *     administratively owned attribute and becomes the correlation key that links a future login to
 *     the academic record. Supplying it does <em>not</em> create a student record — academic
 *     provisioning is a separate operation, deliberately.
 */
public record ProvisionIdentityRequest(
        @NotBlank(message = "is required")
                @Size(max = 100, message = "must be at most 100 characters")
                @Pattern(
                        regexp = "^[A-Za-z0-9._@/-]+$",
                        message = "may contain only letters, digits and . _ @ / -")
                String username,
        @NotBlank(message = "is required")
                @Email(message = "must be a valid email address")
                @Size(max = 255, message = "must be at most 255 characters")
                String email,
        @NotBlank(message = "is required") @Size(max = 100) String firstName,
        @NotBlank(message = "is required") @Size(max = 100) String lastName,
        @Size(max = 30)
                @Pattern(
                        regexp = "^[A-Za-z0-9/-]*$",
                        message = "may contain only letters, digits, hyphens and slashes")
                String studentNumber,
        Set<String> realmRoles) {}
