package com.university.lms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.finance.domain.AccountEntry;
import com.university.lms.finance.domain.AccountEntryType;
import com.university.lms.finance.domain.RefundPolicy;
import com.university.lms.finance.domain.StudentAccount;
import com.university.lms.finance.repository.AccountEntryRepository;
import com.university.lms.finance.repository.FeeCatalogRepository;
import com.university.lms.finance.repository.StudentAccountRepository;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawalRefundTest {

    @Mock
    StudentAccountRepository accountRepository;

    @Mock
    AccountEntryRepository entryRepository;

    @Mock
    PaymentPlanService paymentPlanService;

    @Mock
    TuitionScheduleService tuitionScheduleService;

    @Mock
    FeeCatalogService feeCatalogService;

    @Mock
    FeeCatalogRepository feeRepository;

    @Mock
    StudentDirectory studentDirectory;

    @Mock
    AcademicStructure academicStructure;

    @Mock
    RefundPolicyService refundPolicyService;

    DefaultStudentBilling billing;

    UUID studentId;
    UUID enrollmentId;
    UUID termId;
    StudentAccount account;

    @BeforeEach
    void setUp() {
        billing = new DefaultStudentBilling(
                accountRepository,
                entryRepository,
                paymentPlanService,
                tuitionScheduleService,
                feeCatalogService,
                feeRepository,
                studentDirectory,
                academicStructure,
                refundPolicyService);
        studentId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();
        termId = UUID.randomUUID();
        account = new StudentAccount(studentId, "USD");
        org.mockito.Mockito.lenient().when(feeRepository.findAll()).thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(refundPolicyService.current())
                .thenReturn(new RefundPolicy(
                        7, new BigDecimal("0.75"), 14, new BigDecimal("0.50"), 21, new BigDecimal("0.25")));
    }

    private AcademicStructure.TermCalendar calendarClosedDaysAgo(long days) {
        Instant addDropClosesAt = Instant.now().minus(days, ChronoUnit.DAYS);
        return new AcademicStructure.TermCalendar(
                termId,
                "Fall 2026",
                UUID.randomUUID(),
                "2026",
                LocalDate.now().minusMonths(1),
                LocalDate.now().plusMonths(3),
                Instant.now().minus(60, ChronoUnit.DAYS),
                Instant.now().minus(45, ChronoUnit.DAYS),
                false,
                Instant.now().minus(45, ChronoUnit.DAYS),
                addDropClosesAt,
                false,
                LocalDate.now().plusMonths(1),
                "IN_SESSION");
    }

    private AccountEntry chargeOf(BigDecimal amount, String reference) {
        return new AccountEntry(account, AccountEntryType.CHARGE, amount, "Tuition — CMP1024", Instant.now(), reference);
    }

    @Test
    @DisplayName("E4: within a week of add/drop closing, a withdrawal earns a 75% refund")
    void firstTierRefundsSeventyFivePercent() {
        when(accountRepository.lockByStudentId(studentId)).thenReturn(Optional.of(account));
        when(academicStructure.findCalendar(eq(termId), any())).thenReturn(Optional.of(calendarClosedDaysAgo(3)));
        String tuitionRef = DefaultStudentBilling.tuitionReference(enrollmentId);
        String creditRef = DefaultStudentBilling.tuitionCreditReference(enrollmentId);
        when(entryRepository.existsByAccountIdAndReference(account.getId(), creditRef)).thenReturn(false);
        when(entryRepository.findByAccountIdAndReference(account.getId(), tuitionRef))
                .thenReturn(Optional.of(chargeOf(new BigDecimal("1000.00"), tuitionRef)));

        billing.creditForWithdrawal(studentId, enrollmentId, termId, "CMP1024", true);

        ArgumentCaptor<AccountEntry> saved = ArgumentCaptor.forClass(AccountEntry.class);
        verify(entryRepository).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("-750.00");
    }

    @Test
    @DisplayName("E4: two weeks past add/drop closing, a withdrawal earns a 50% refund")
    void secondTierRefundsFiftyPercent() {
        when(accountRepository.lockByStudentId(studentId)).thenReturn(Optional.of(account));
        when(academicStructure.findCalendar(eq(termId), any())).thenReturn(Optional.of(calendarClosedDaysAgo(10)));
        String tuitionRef = DefaultStudentBilling.tuitionReference(enrollmentId);
        when(entryRepository.findByAccountIdAndReference(account.getId(), tuitionRef))
                .thenReturn(Optional.of(chargeOf(new BigDecimal("1000.00"), tuitionRef)));

        billing.creditForWithdrawal(studentId, enrollmentId, termId, "CMP1024", true);

        ArgumentCaptor<AccountEntry> saved = ArgumentCaptor.forClass(AccountEntry.class);
        verify(entryRepository).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("-500.00");
    }

    @Test
    @DisplayName("E4: once the refund window has fully lapsed, a withdrawal posts no credit at all")
    void noTierPostsNoCredit() {
        when(academicStructure.findCalendar(eq(termId), any())).thenReturn(Optional.of(calendarClosedDaysAgo(30)));

        billing.creditForWithdrawal(studentId, enrollmentId, termId, "CMP1024", true);

        verify(entryRepository, never()).save(any());
        verify(accountRepository, never()).lockByStudentId(any());
    }

    @Test
    @DisplayName("E4: a term with no configured add/drop close date posts no credit")
    void noConfiguredCalendarPostsNoCredit() {
        when(academicStructure.findCalendar(eq(termId), any())).thenReturn(Optional.empty());

        billing.creditForWithdrawal(studentId, enrollmentId, termId, "CMP1024", true);

        verify(entryRepository, never()).save(any());
    }
}
