package com.university.lms.finance.dto;

import com.university.lms.finance.domain.FeeAssessment;
import com.university.lms.finance.domain.FeeKind;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateFeeRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 500) String description,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull FeeKind kind,
        @NotNull FeeAssessment assessment,
        UUID courseId,
        UUID programmeId) {}
