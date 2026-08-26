package com.university.lms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.administration.api.RecordAccessLog;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.finance.config.FinanceProperties;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.finance.domain.AccountEntry;
import com.university.lms.finance.domain.AccountEntryStatus;
import com.university.lms.finance.domain.AccountEntryType;
import com.university.lms.finance.domain.FinanceErrorCode;
import com.university.lms.finance.domain.StudentAccount;
import com.university.lms.finance.dto.AccountResponse;
import com.university.lms.finance.dto.CreatePaymentRequest;
import com.university.lms.finance.repository.AccountEntryRepository;
import com.university.lms.finance.repository.StudentAccountRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID REGISTRAR_USER_ID = UUID.randomUUID();
    private static final CurrentUser STUDENT = new CurrentUser(
            USER_ID,
            "sub",
            "202012345",
            "student@university.test",
            "Demo Student",
            Optional.of("202012345"),
            Set.of(SecurityRoles.STUDENT),
            Set.of());
    private static final CurrentUser REGISTRAR = new CurrentUser(
            REGISTRAR_USER_ID,
            "sub-registrar",
            "registrar",
            "registrar@university.test",
            "Rita Registrar",
            Optional.empty(),
            Set.of(SecurityRoles.REGISTRAR),
            Set.of());
    private static final UUID BURSAR_USER_ID = UUID.randomUUID();
    private static final CurrentUser BURSAR = new CurrentUser(
            BURSAR_USER_ID,
            "sub-bursar",
            "bursar",
            "bursar@university.test",
            "Beau Bursar",
            Optional.empty(),
            Set.of(SecurityRoles.BURSAR),
            Set.of());

    @Mock
    private StudentAccountRepository accountRepository;

    @Mock
    private AccountEntryRepository entryRepository;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private PaymentPlanService paymentPlanService;

    @Mock
    private RecordAccessLog recordAccessLog;

    @Mock
    private AuditTrail auditTrail;

    private FinanceService service;

    @BeforeEach
    void callerIsTheStudent() {
        lenient().when(currentUserProvider.require()).thenReturn(STUDENT);
        lenient().when(studentDirectory.studentIdOfUser(USER_ID)).thenReturn(Optional.of(STUDENT_ID));
        service = new FinanceService(
                accountRepository,
                entryRepository,
                studentDirectory,
                currentUserProvider,
                paymentPlanService,
                new FinanceProperties(true),
                recordAccessLog,
                auditTrail);
    }

    @Test
    void postsAPaymentUpToTheOutstandingBalance() {
        StudentAccount account = new StudentAccount(STUDENT_ID, "USD");
        when(accountRepository.lockByStudentId(STUDENT_ID)).thenReturn(Optional.of(account));
        when(entryRepository.findByAccountIdOrderByOccurredAtAsc(account.getId()))
                .thenReturn(List.of(new AccountEntry(
                        account, AccountEntryType.CHARGE, new BigDecimal("100.00"), "Tuition", Instant.now())));
        when(entryRepository.save(any(AccountEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        var paid = service.payOwn(new CreatePaymentRequest(new BigDecimal("40.00")));

        assertThat(paid.amount()).isEqualByComparingTo("40.00");
        assertThat(paid.balance()).isEqualByComparingTo("60.00");
        assertThat(paid.currency()).isEqualTo("USD");
    }

    @Test
    void refusesWhenTheAmountExceedsWhatIsOwed() {
        StudentAccount account = new StudentAccount(STUDENT_ID, "USD");
        when(accountRepository.lockByStudentId(STUDENT_ID)).thenReturn(Optional.of(account));
        when(entryRepository.findByAccountIdOrderByOccurredAtAsc(account.getId()))
                .thenReturn(List.of(new AccountEntry(
                        account, AccountEntryType.CHARGE, new BigDecimal("10.00"), "Fee", Instant.now())));

        assertThatThrownBy(() -> service.payOwn(new CreatePaymentRequest(new BigDecimal("10.01"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(FinanceErrorCode.PAYMENT_EXCEEDS_BALANCE));
    }

    @Test
    void refusesWhenNothingIsDue() {
        when(accountRepository.lockByStudentId(STUDENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payOwn(new CreatePaymentRequest(new BigDecimal("1.00"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(FinanceErrorCode.PAYMENT_NOTHING_DUE));
    }

    @Test
    void refusesEveryPaymentWhenSelfServiceIsDisabled() {
        FinanceService disabled = new FinanceService(
                accountRepository,
                entryRepository,
                studentDirectory,
                currentUserProvider,
                paymentPlanService,
                new FinanceProperties(false),
                recordAccessLog,
                auditTrail);

        assertThatThrownBy(() -> disabled.payOwn(new CreatePaymentRequest(new BigDecimal("1.00"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(FinanceErrorCode.SELF_SERVICE_PAYMENT_DISABLED));
    }

    @Test
    @DisplayName("E3: a proposed manual ledger entry does not affect the balance until approved")
    void proposedLedgerEntryIsPendingAndAudited() {
        when(currentUserProvider.require()).thenReturn(REGISTRAR);
        when(studentDirectory.exists(STUDENT_ID)).thenReturn(true);
        StudentAccount account = new StudentAccount(STUDENT_ID, "USD");
        when(accountRepository.findByStudentId(STUDENT_ID)).thenReturn(Optional.of(account));
        when(entryRepository.save(any(AccountEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        when(entryRepository.findByAccountIdOrderByOccurredAtAsc(account.getId())).thenAnswer(inv -> List.of());

        AccountResponse response = service.proposeEntry(
                STUDENT_ID,
                new com.university.lms.finance.dto.CreateAccountEntryRequest(
                        AccountEntryType.CREDIT, new BigDecimal("50.00"), "Manual fee waiver", null, null, null));

        // Nothing posted yet — proposing must not move the balance a second staff member has not approved.
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(auditTrail)
                .record(
                        eq(REGISTRAR_USER_ID),
                        eq("Rita Registrar"),
                        eq(AuditTrail.Action.LEDGER_ENTRY_PROPOSED),
                        eq(AuditTrail.EntityType.ACCOUNT_ENTRY),
                        any(UUID.class),
                        any(),
                        eq("Manual fee waiver"),
                        isNull(),
                        any());
    }

    @Test
    @DisplayName("E3: the same staff member who proposed an entry cannot approve it")
    void proposerCannotApproveTheirOwnEntry() {
        when(currentUserProvider.require()).thenReturn(REGISTRAR);
        StudentAccount account = new StudentAccount(STUDENT_ID, "USD");
        AccountEntry entry = AccountEntry.propose(
                account, AccountEntryType.CREDIT, new BigDecimal("-50.00"), "Manual fee waiver", Instant.now(),
                REGISTRAR_USER_ID);
        when(accountRepository.findByStudentId(STUDENT_ID)).thenReturn(Optional.of(account));
        when(entryRepository.findById(any())).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.approveEntry(STUDENT_ID, entry.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(FinanceErrorCode.LEDGER_ENTRY_SELF_APPROVAL));
    }

    @Test
    @DisplayName("E3: a different staff member approving a proposed entry posts it to the balance")
    void aDifferentStaffMemberApprovingPostsTheEntry() {
        UUID otherStaffId = UUID.randomUUID();
        CurrentUser otherStaff = new CurrentUser(
                otherStaffId, "sub-other", "other", "other@test.edu", "Other Staff",
                java.util.Optional.empty(), java.util.Set.of("REGISTRAR"), java.util.Set.of());
        when(currentUserProvider.require()).thenReturn(otherStaff);
        StudentAccount account = new StudentAccount(STUDENT_ID, "USD");
        AccountEntry entry = AccountEntry.propose(
                account, AccountEntryType.CREDIT, new BigDecimal("-50.00"), "Manual fee waiver", Instant.now(),
                REGISTRAR_USER_ID);
        when(accountRepository.findByStudentId(STUDENT_ID)).thenReturn(Optional.of(account));
        when(entryRepository.findById(any())).thenReturn(Optional.of(entry));
        when(entryRepository.save(any(AccountEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        when(entryRepository.findByAccountIdOrderByOccurredAtAsc(account.getId())).thenReturn(List.of(entry));

        AccountResponse response = service.approveEntry(STUDENT_ID, entry.getId());

        assertThat(entry.getStatus()).isEqualTo(AccountEntryStatus.POSTED);
        assertThat(entry.getDecidedBy()).isEqualTo(otherStaffId);
        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("-50.00"));
        verify(auditTrail)
                .record(
                        eq(otherStaffId),
                        any(),
                        eq(AuditTrail.Action.LEDGER_ENTRY_APPROVED),
                        eq(AuditTrail.EntityType.ACCOUNT_ENTRY),
                        any(UUID.class),
                        any(),
                        any(),
                        isNull(),
                        any());
    }

    @Test
    @DisplayName("A7: a registrar viewing a student's account is logged as a FERPA disclosure")
    void staffViewingAStudentsAccountIsLogged() {
        when(currentUserProvider.require()).thenReturn(REGISTRAR);
        when(studentDirectory.exists(STUDENT_ID)).thenReturn(true);
        when(accountRepository.findByStudentId(STUDENT_ID)).thenReturn(Optional.empty());

        service.forStudent(STUDENT_ID);

        verify(recordAccessLog)
                .record(
                        REGISTRAR_USER_ID,
                        "Rita Registrar",
                        STUDENT_ID,
                        RecordAccessLog.RecordType.FINANCE,
                        RecordAccessLog.Action.VIEW,
                        "Student account");
    }

    @Test
    @DisplayName("A6: BURSAR is additionally accepted for ledger access, alongside REGISTRAR — not instead of it")
    void bursarCanAccessTheLedgerAlongsideRegistrar() {
        when(currentUserProvider.require()).thenReturn(BURSAR);
        when(studentDirectory.exists(STUDENT_ID)).thenReturn(true);
        when(accountRepository.findByStudentId(STUDENT_ID)).thenReturn(Optional.empty());

        var account = service.forStudent(STUDENT_ID);

        assertThat(account).isNotNull();
    }
}
