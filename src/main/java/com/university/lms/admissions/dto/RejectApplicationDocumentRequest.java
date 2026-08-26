package com.university.lms.admissions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectApplicationDocumentRequest(
        @NotBlank(message = "is required") @Size(max = 500, message = "must be at most 500 characters")
                String reason) {}
