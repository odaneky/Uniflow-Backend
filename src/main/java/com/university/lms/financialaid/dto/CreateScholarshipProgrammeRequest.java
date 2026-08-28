package com.university.lms.financialaid.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateScholarshipProgrammeRequest(
        @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String name,
        @Size(max = 200, message = "must be at most 200 characters") String sponsorName,
        @Size(max = 2000, message = "must be at most 2000 characters") String description,
        @NotNull(message = "is required") @DecimalMin(value = "0.01", message = "must be greater than zero")
                BigDecimal defaultAmount,
        boolean renewable,
        @Min(value = 0, message = "must be zero or more") Integer maxRenewals,
        @Size(max = 2000, message = "must be at most 2000 characters") String eligibilityCriteria) {}
