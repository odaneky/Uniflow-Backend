package com.university.lms.staffing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record AppointStaffRequest(
        @NotNull(message = "is required") UUID userId,
        @NotNull(message = "is required") UUID orgUnitId,
        @NotBlank(message = "is required") @Size(max = 50, message = "must be at most 50 characters") String role,
        @NotNull(message = "is required") @PastOrPresent(message = "must not be in the future") LocalDate validFrom) {}
