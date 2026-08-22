package com.university.lms.finance.dto;

import com.university.lms.finance.domain.AccountEntryType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CreateAccountEntryRequest(
        @NotNull(message = "is required") AccountEntryType entryType,
        @NotNull(message = "is required") @DecimalMin(value = "0.01", message = "must be greater than zero")
                BigDecimal amount,
        @NotBlank(message = "is required") @Size(max = 500, message = "must be at most 500 characters")
                String description,
        Instant occurredAt,
        String currency,
        LocalDate dueOn) {}
