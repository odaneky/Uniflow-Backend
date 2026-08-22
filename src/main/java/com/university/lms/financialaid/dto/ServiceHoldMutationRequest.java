package com.university.lms.financialaid.dto;

import com.university.lms.financialaid.domain.HoldType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ServiceHoldMutationRequest(
        @NotNull(message = "is required") Action action,
        HoldType holdType,
        @Size(max = 500, message = "must be at most 500 characters") String reason,
        UUID holdId) {

    public enum Action {
        PLACE,
        CLEAR
    }
}
