package com.university.lms.disciplinary.dto;

import com.university.lms.disciplinary.domain.DisciplinaryCaseStatus;
import com.university.lms.disciplinary.domain.DisciplinaryOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code status} must be {@code RESOLVED} or {@code DISMISSED} — see DisciplinaryCase.close. */
public record ResolveDisciplinaryCaseRequest(
        @NotNull(message = "is required") DisciplinaryCaseStatus status,
        @NotNull(message = "is required") DisciplinaryOutcome outcome,
        @NotBlank(message = "is required") @Size(max = 2000, message = "must be at most 2000 characters")
                String reason) {}
