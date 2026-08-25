package com.university.lms.student.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateAdvisingAppointmentRequest(
        @NotNull(message = "is required") Instant scheduledAt,
        @NotNull(message = "is required")
                @Positive(message = "must be greater than zero")
                @Max(value = 480, message = "must be at most 480 minutes")
                Integer durationMinutes,
        @Size(max = 1000, message = "must be at most 1000 characters") String note) {}
