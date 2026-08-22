package com.university.lms.admissions.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record CreateApplicationRequest(
        @NotBlank(message = "is required")
                @Email(message = "must be a valid email address")
                @Size(max = 255, message = "must be at most 255 characters")
                String applicantEmail,
        @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters")
                String applicantName,
        @NotNull(message = "is required") UUID programmeId,
        @NotNull(message = "is required") UUID academicTermId,
        Map<String, Object> payload,
        Boolean submit) {}
