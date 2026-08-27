package com.university.lms.finance.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record IssueInvoiceRequest(
        @NotNull(message = "is required") UUID academicTermId,
        @NotNull(message = "is required") @FutureOrPresent(message = "cannot be in the past") LocalDate dueOn,
        @Size(max = 200, message = "must be at most 200 characters") String billToName,
        @Email(message = "must be a valid email") @Size(max = 255, message = "must be at most 255 characters")
                String billToEmail,
        @Size(max = 1000, message = "must be at most 1000 characters") String notes) {}
