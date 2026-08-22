package com.university.lms.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Student-initiated campus cashier payment against their own ledger. */
public record CreatePaymentRequest(
        @NotNull(message = "is required")
                @DecimalMin(value = "0.01", message = "must be greater than zero")
                @Digits(integer = 10, fraction = 2, message = "must have at most two decimal places")
                BigDecimal amount) {}
