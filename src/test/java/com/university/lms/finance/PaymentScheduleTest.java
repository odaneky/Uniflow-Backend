package com.university.lms.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.finance.api.PaymentStanding;
import com.university.lms.finance.domain.PaymentSchedule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentScheduleTest {

    private static final UUID TERM = UUID.randomUUID();

    @Test
    void dueBeforeWeekIsTheDayBeforeThatTeachingWeekStarts() {
        LocalDate start = LocalDate.of(2026, 8, 24);
        assertThat(PaymentSchedule.dueBeforeWeek(start, 4)).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(PaymentSchedule.dueBeforeWeek(start, 8)).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(PaymentSchedule.dueBeforeWeek(start, 11)).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void unpaidPastInstallmentPlacesHoldAndExamBlock() {
        List<PaymentSchedule.Step> steps = List.of(
                new PaymentSchedule.Step("Week 4", 30, 4, LocalDate.of(2026, 9, 13), true, false),
                new PaymentSchedule.Step("Week 8", 70, 8, LocalDate.of(2026, 10, 11), true, false),
                new PaymentSchedule.Step("Week 11", 100, 11, LocalDate.of(2026, 11, 1), true, true));
        PaymentStanding standing = PaymentSchedule.evaluate(
                TERM, steps, new BigDecimal("1000.00"), new BigDecimal("200.00"), LocalDate.of(2026, 9, 14));

        assertThat(standing.percentPaid()).isEqualTo(20);
        assertThat(standing.percentRequired()).isEqualTo(30);
        assertThat(standing.amountDueNow()).isEqualByComparingTo("100.00");
        assertThat(standing.hold()).isTrue();
        assertThat(standing.examBlocked()).isFalse();
        assertThat(standing.nextLabel()).isEqualTo("Week 8");
    }

    @Test
    void examBlockFiresOnlyWhenThatInstallmentIsDueAndUnmet() {
        List<PaymentSchedule.Step> steps = List.of(
                new PaymentSchedule.Step("Week 11", 100, 11, LocalDate.of(2026, 11, 1), true, true));
        PaymentStanding standing = PaymentSchedule.evaluate(
                TERM, steps, new BigDecimal("1000.00"), BigDecimal.ZERO, LocalDate.of(2026, 11, 2));

        assertThat(standing.examBlocked()).isTrue();
        assertThat(standing.hold()).isTrue();
        assertThat(standing.percentRequired()).isEqualTo(100);
    }
}
