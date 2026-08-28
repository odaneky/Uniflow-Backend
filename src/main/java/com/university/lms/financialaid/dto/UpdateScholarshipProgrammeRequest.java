package com.university.lms.financialaid.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Partial update: a null field keeps the programme's current value. */
public record UpdateScholarshipProgrammeRequest(
        @Size(max = 200, message = "must be at most 200 characters") String name,
        @Size(max = 200, message = "must be at most 200 characters") String sponsorName,
        Boolean clearSponsorName,
        @Size(max = 2000, message = "must be at most 2000 characters") String description,
        @DecimalMin(value = "0.01", message = "must be greater than zero") BigDecimal defaultAmount,
        Boolean renewable,
        @Min(value = 0, message = "must be zero or more") Integer maxRenewals,
        Boolean clearMaxRenewals,
        @Size(max = 2000, message = "must be at most 2000 characters") String eligibilityCriteria,
        Boolean active) {}
