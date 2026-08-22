package com.university.lms.communication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMessageRequest(
        @NotBlank(message = "is required") @Size(max = 4000, message = "must be at most 4000 characters") String body) {}
