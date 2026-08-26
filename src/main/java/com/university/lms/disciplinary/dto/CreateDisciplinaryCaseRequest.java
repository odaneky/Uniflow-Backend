package com.university.lms.disciplinary.dto;

import com.university.lms.disciplinary.domain.DisciplinaryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateDisciplinaryCaseRequest(
        @NotNull(message = "is required") UUID studentId,
        @NotNull(message = "is required") DisciplinaryCategory category,
        @NotBlank(message = "is required") @Size(max = 2000, message = "must be at most 2000 characters")
                String summary) {}
