package com.university.lms.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateRoomRequest(
        @NotNull(message = "is required") UUID buildingId,
        @NotBlank(message = "is required") @Size(max = 50, message = "must be at most 50 characters") String code,
        @NotNull(message = "is required") @Positive(message = "must be greater than zero") Integer capacity) {}
