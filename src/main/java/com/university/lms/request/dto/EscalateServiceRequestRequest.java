package com.university.lms.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** D9: raising a request for supervisory attention requires saying why. */
public record EscalateServiceRequestRequest(
        @NotBlank(message = "is required") @Size(max = 1000, message = "must be at most 1000 characters")
                String reason) {}
