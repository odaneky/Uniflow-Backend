package com.university.lms.finance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.university.lms.finance.api.PaymentStanding;
import com.university.lms.finance.api.PaymentStanding.InstallmentStanding;

/**
 * Cumulative percent-due rules. Dates are either explicit or "before week N of the term"
 * (the day before that teaching week begins).
 */
public final class PaymentSchedule {

    private PaymentSchedule() {}

    public record Step(
            String label,
            int cumulativePercent,
            Integer weekOfTerm,
            LocalDate dueOn,
            boolean placesHold,
            boolean blocksExams) {}

    /** Due the calendar day before teaching week {@code weekOfTerm} starts. */
    public static LocalDate dueBeforeWeek(LocalDate termStart, int weekOfTerm) {
        return termStart.plusWeeks(weekOfTerm - 1L).minusDays(1);
    }

    public static PaymentStanding evaluate(
            UUID academicTermId, List<Step> steps, BigDecimal charges, BigDecimal paid, LocalDate asOf) {
        BigDecimal chargeTotal = charges == null ? BigDecimal.ZERO : charges.max(BigDecimal.ZERO);
        BigDecimal paidTotal = paid == null ? BigDecimal.ZERO : paid.max(BigDecimal.ZERO);
        int percentPaid = chargeTotal.signum() == 0
                ? 100
                : paidTotal.multiply(BigDecimal.valueOf(100)).divide(chargeTotal, 0, RoundingMode.DOWN).intValue();

        List<InstallmentStanding> rows = new ArrayList<>();
        int percentRequired = 0;
        BigDecimal amountDueNow = BigDecimal.ZERO;
        boolean hold = false;
        boolean examBlocked = false;
        String reason = null;
        LocalDate nextDueOn = null;
        String nextLabel = null;

        for (Step step : steps) {
            BigDecimal required = chargeTotal
                    .multiply(BigDecimal.valueOf(step.cumulativePercent()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            boolean dueReached = !asOf.isBefore(step.dueOn());
            boolean met = paidTotal.compareTo(required) >= 0;
            BigDecimal stillDue = required.subtract(paidTotal).max(BigDecimal.ZERO);
            rows.add(new InstallmentStanding(
                    step.label(),
                    step.cumulativePercent(),
                    step.weekOfTerm(),
                    step.dueOn(),
                    stillDue,
                    dueReached,
                    met,
                    step.placesHold(),
                    step.blocksExams()));
            if (dueReached && !met) {
                percentRequired = Math.max(percentRequired, step.cumulativePercent());
                amountDueNow = amountDueNow.max(stillDue);
                if (step.placesHold()) {
                    hold = true;
                }
                if (step.blocksExams()) {
                    examBlocked = true;
                }
                if (reason == null) {
                    reason = "Pay " + step.cumulativePercent() + "% by " + step.label();
                }
            }
            if (!dueReached && nextDueOn == null) {
                nextDueOn = step.dueOn();
                nextLabel = step.label();
            }
        }

        return new PaymentStanding(
                academicTermId,
                chargeTotal,
                paidTotal,
                percentPaid,
                percentRequired,
                amountDueNow,
                nextDueOn,
                nextLabel,
                hold,
                examBlocked,
                reason,
                List.copyOf(rows));
    }
}
