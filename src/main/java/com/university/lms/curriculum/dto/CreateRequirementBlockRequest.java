package com.university.lms.curriculum.dto;

import com.university.lms.curriculum.domain.RequirementKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateRequirementBlockRequest(
        @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String name,
        @NotNull(message = "is required") RequirementKind kind,
        @NotNull(message = "is required") @Positive(message = "must be greater than zero") Integer requiredCredits,
        Integer position,
        List<UUID> courseIds) {}
