package com.university.lms.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAdvisingNoteRequest(
        @NotBlank(message = "is required") @Size(max = 2000, message = "must be at most 2000 characters")
                String note) {}
