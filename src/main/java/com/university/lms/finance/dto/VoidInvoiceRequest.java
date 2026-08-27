package com.university.lms.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidInvoiceRequest(
        @NotBlank(message = "is required") @Size(max = 500, message = "must be at most 500 characters")
                String reason) {}
