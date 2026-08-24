package com.university.lms.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReportExamMisconductRequest(
        @NotNull(message = "is required") UUID studentId,
        @NotBlank(message = "is required") @Size(max = 2000, message = "must be at most 2000 characters")
                String description) {}
