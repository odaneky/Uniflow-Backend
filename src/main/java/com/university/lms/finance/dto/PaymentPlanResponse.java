package com.university.lms.finance.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentPlanResponse(
        UUID academicTermId, String termName, LocalDate termStart, List<InstallmentResponse> installments) {

    public record InstallmentResponse(
            String label,
            int cumulativePercent,
            Integer weekOfTerm,
            LocalDate dueOn,
            boolean placesHold,
            boolean blocksExams) {}
}
