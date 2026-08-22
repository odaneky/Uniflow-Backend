package com.university.lms.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ReplaceTuitionScheduleRequest(
        @NotNull @DecimalMin(value = "0.01", message = "must be greater than zero") BigDecimal amountPerCredit,
        @NotNull @DecimalMin(value = "0.01", message = "must be greater than zero") BigDecimal campusFee) {}
