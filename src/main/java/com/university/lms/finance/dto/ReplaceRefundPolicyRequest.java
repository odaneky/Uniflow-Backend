package com.university.lms.finance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ReplaceRefundPolicyRequest(
        @Positive(message = "must be greater than zero") int tier1Days,
        @NotNull @DecimalMin(value = "0", message = "must be at least zero")
                @DecimalMax(value = "1", message = "must be at most one")
                BigDecimal tier1Pct,
        @Positive(message = "must be greater than zero") int tier2Days,
        @NotNull @DecimalMin(value = "0", message = "must be at least zero")
                @DecimalMax(value = "1", message = "must be at most one")
                BigDecimal tier2Pct,
        @Positive(message = "must be greater than zero") int tier3Days,
        @NotNull @DecimalMin(value = "0", message = "must be at least zero")
                @DecimalMax(value = "1", message = "must be at most one")
                BigDecimal tier3Pct) {}
