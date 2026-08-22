package com.university.lms.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record ReplacePaymentPlanRequest(@NotNull @Valid List<InstallmentRequest> installments) {

    public record InstallmentRequest(
            @NotBlank @Size(max = 80) String label,
            @NotNull @Min(1) @Max(100) Integer cumulativePercent,
            @Min(1) @Max(20) Integer weekOfTerm,
            LocalDate dueOn,
            Boolean placesHold,
            Boolean blocksExams) {}
}
