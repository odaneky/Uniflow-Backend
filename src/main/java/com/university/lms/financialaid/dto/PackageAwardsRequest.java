package com.university.lms.financialaid.dto;

import com.university.lms.financialaid.domain.AwardType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record PackageAwardsRequest(
        @NotNull(message = "is required") UUID academicTermId,
        String aidYear,
        @DecimalMin(value = "0.01", message = "must be greater than zero") BigDecimal pellAmount,
        @DecimalMin(value = "0.01", message = "must be greater than zero") BigDecimal institutionalAmount) {

    public AwardType resolvedPellType() {
        return AwardType.PELL;
    }

    public AwardType resolvedInstitutionalType() {
        return AwardType.INSTITUTIONAL;
    }
}
