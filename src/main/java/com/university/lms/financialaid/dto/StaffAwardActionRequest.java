package com.university.lms.financialaid.dto;

import com.university.lms.financialaid.domain.AwardType;
import com.university.lms.financialaid.domain.HoldType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record StaffAwardActionRequest(
        @NotNull(message = "is required") Action action,
        UUID academicTermId,
        String aidYear,
        @DecimalMin(value = "0.01", message = "must be greater than zero") BigDecimal pellAmount,
        @DecimalMin(value = "0.01", message = "must be greater than zero") BigDecimal institutionalAmount,
        UUID awardId,
        AwardType awardType,
        @DecimalMin(value = "0.01", message = "must be greater than zero") BigDecimal amount,
        HoldType holdType,
        @Size(max = 500, message = "must be at most 500 characters") String reason,
        UUID holdId) {

    public enum Action {
        PACKAGE,
        DISBURSE,
        PLACE_HOLD,
        CLEAR_HOLD
    }
}
