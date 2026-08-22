package com.university.lms.finance.dto;

import com.university.lms.finance.domain.FeeAssessment;
import com.university.lms.finance.domain.FeeKind;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record UpdateFeeRequest(
        @Size(max = 80) String name,
        @Size(max = 500) String description,
        @DecimalMin(value = "0.01") BigDecimal amount,
        FeeKind kind,
        FeeAssessment assessment,
        UUID courseId,
        UUID programmeId,
        Boolean clearCourseId,
        Boolean clearProgrammeId,
        Boolean active) {}
