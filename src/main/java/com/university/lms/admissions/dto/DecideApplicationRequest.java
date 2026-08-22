package com.university.lms.admissions.dto;

import com.university.lms.admissions.domain.AdmissionDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record DecideApplicationRequest(
        @NotNull(message = "is required") AdmissionDecision decision,
        @Size(max = 2000, message = "must be at most 2000 characters") String note,
        BigDecimal depositAmount) {}
