package com.university.lms.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** How much of this term's charges must already have been paid, and what happens if they have not. */
public record PaymentStanding(
        UUID academicTermId,
        BigDecimal charges,
        BigDecimal paid,
        int percentPaid,
        int percentRequired,
        BigDecimal amountDueNow,
        LocalDate nextDueOn,
        String nextLabel,
        boolean hold,
        boolean examBlocked,
        String reason,
        List<InstallmentStanding> installments) {

    public static PaymentStanding none() {
        return new PaymentStanding(
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                100,
                0,
                BigDecimal.ZERO,
                null,
                null,
                false,
                false,
                null,
                List.of());
    }

    public record InstallmentStanding(
            String label,
            int cumulativePercent,
            Integer weekOfTerm,
            LocalDate dueOn,
            BigDecimal amountDue,
            boolean dueReached,
            boolean met,
            boolean placesHold,
            boolean blocksExams) {}
}
