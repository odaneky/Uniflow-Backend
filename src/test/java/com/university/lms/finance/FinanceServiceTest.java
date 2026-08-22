package com.university.lms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.finance.domain.AccountEntry;
import com.university.lms.finance.domain.AccountEntryType;
import com.university.lms.finance.domain.FinanceErrorCode;
import com.university.lms.finance.domain.StudentAccount;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final CurrentUser STUDENT = new CurrentUser(
            USER_ID,
            "sub",
            "202012345",
            "student@university.test",
            "Demo Student",
            Optional.of("202012345"),
            Set.of(SecurityRoles.STUDENT),
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

    @InjectMocks
    private FinanceService service;

    @BeforeEach
    void callerIsTheStudent() {
        when(currentUserProvider.require()).thenReturn(STUDENT);
        when(studentDirectory.studentIdOfUser(USER_ID)).thenReturn(Optional.of(STUDENT_ID));
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
}
