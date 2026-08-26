package com.university.lms.disciplinary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDisciplinaryCaseNoteRequest(
        @NotBlank(message = "is required") @Size(max = 2000, message = "must be at most 2000 characters")
                String note) {}
