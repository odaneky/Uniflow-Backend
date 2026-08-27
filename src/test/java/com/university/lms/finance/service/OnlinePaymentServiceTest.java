package com.university.lms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.finance.domain.AccountEntry;
import com.university.lms.finance.domain.AccountEntryType;
import com.university.lms.finance.domain.FinanceErrorCode;
import com.university.lms.finance.domain.PendingPayment;
import com.university.lms.finance.domain.PendingPaymentStatus;
import com.university.lms.finance.domain.StudentAccount;
import com.university.lms.finance.dto.CreatePaymentRequest;
import com.university.lms.finance.dto.OnlinePaymentResponse;
import com.university.lms.finance.gateway.PaymentGateway;
import com.university.lms.finance.repository.AccountEntryRepository;
import com.university.lms.finance.repository.PendingPaymentRepository;
import com.university.lms.finance.repository.StudentAccountRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
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
class OnlinePaymentServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final CurrentUser STUDENT = new CurrentUser(
            USER_ID,
            "sub",
            "202099999",
            "student@university.test",
            "Sam Student",
            Optional.of("202099999"),
            Set.of(SecurityRoles.STUDENT),
            Set.of());

    @Mock
    private PendingPaymentRepository pendingPaymentRepository;

    @Mock
    private AccountEntryRepository entryRepository;

    @Mock
    private StudentAccountRepository accountRepository;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private AuditTrail auditTrail;

    private OnlinePaymentService service;
    private StudentAccount account;

    @BeforeEach
    void setUp() {
        service = new OnlinePaymentService(
                pendingPaymentRepository,
                entryRepository,
                accountRepository,
                studentDirectory,
                currentUserProvider,
                paymentGateway,
                auditTrail);

        account = new StudentAccount(STUDENT_ID, "USD");
        lenient().when(currentUserProvider.require()).thenReturn(STUDENT);
        lenient().when(studentDirectory.studentIdOfUser(USER_ID)).thenReturn(Optional.of(STUDENT_ID));
        lenient().when(accountRepository.lockByStudentId(STUDENT_ID)).thenReturn(Optional.of(account));
        lenient().when(pendingPaymentRepository.save(any(PendingPayment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private AccountEntry charge(BigDecimal amount) {
        return new AccountEntry(account, AccountEntryType.CHARGE, amount, "Tuition", java.time.Instant.now());
    }

    @Test
    @DisplayName("initiating creates a pending payment and returns the gateway's redirect URL")
    void initiatingCreatesAPendingPayment() {
        when(paymentGateway.configured()).thenReturn(true);
        when(entryRepository.findByAccountIdOrderByOccurredAtAsc(account.getId()))
                .thenReturn(List.of(charge(new BigDecimal("500.00"))));
        when(paymentGateway.createCheckoutSession(any(), any(), any()))
                .thenReturn(new PaymentGateway.CheckoutSession("cs_test_123", "https://checkout.stripe.com/pay/cs_test_123"));

        OnlinePaymentResponse response = service.initiate(new CreatePaymentRequest(new BigDecimal("200.00")));

        assertThat(response.redirectUrl()).isEqualTo("https://checkout.stripe.com/pay/cs_test_123");
        verify(auditTrail)
                .record(
                        org.mockito.ArgumentMatchers.eq(USER_ID),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(AuditTrail.Action.ONLINE_PAYMENT_INITIATED),
                        org.mockito.ArgumentMatchers.eq(AuditTrail.EntityType.PENDING_PAYMENT),
                        any(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("initiating is refused when no gateway is configured")
    void initiatingWithoutAGatewayIsRefused() {
        when(paymentGateway.configured()).thenReturn(false);

        assertThatThrownBy(() -> service.initiate(new CreatePaymentRequest(new BigDecimal("50.00"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(FinanceErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED));
        verify(paymentGateway, never()).createCheckoutSession(any(), any(), any());
    }

    @Test
    @DisplayName("initiating more than the outstanding balance is refused")
    void initiatingMoreThanTheBalanceIsRefused() {
        when(paymentGateway.configured()).thenReturn(true);
        when(entryRepository.findByAccountIdOrderByOccurredAtAsc(account.getId()))
                .thenReturn(List.of(charge(new BigDecimal("100.00"))));

        assertThatThrownBy(() -> service.initiate(new CreatePaymentRequest(new BigDecimal("200.00"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(FinanceErrorCode.PAYMENT_EXCEEDS_BALANCE));
    }

    @Test
    @DisplayName("a successful webhook posts the real payment and settles the pending one")
    void aSuccessfulWebhookPostsThePayment() {
        PendingPayment pending = new PendingPayment(account, new BigDecimal("200.00"), "USD", "STRIPE", "cs_test_123");
        when(paymentGateway.parseWebhook("payload", "sig"))
                .thenReturn(Optional.of(new PaymentGateway.WebhookResult("cs_test_123", true, null)));
        when(pendingPaymentRepository.findByProviderAndProviderReference("STRIPE", "cs_test_123"))
                .thenReturn(Optional.of(pending));
        when(entryRepository.save(any(AccountEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handleWebhook("payload", "sig");

        assertThat(pending.getStatus()).isEqualTo(PendingPaymentStatus.SETTLED);
        verify(entryRepository).save(any(AccountEntry.class));
        verify(auditTrail)
                .record(
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.eq("Stripe webhook"),
                        org.mockito.ArgumentMatchers.eq(AuditTrail.Action.ONLINE_PAYMENT_SETTLED),
                        org.mockito.ArgumentMatchers.eq(AuditTrail.EntityType.PENDING_PAYMENT),
                        any(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("a failed webhook marks the pending payment failed without touching the ledger")
    void aFailedWebhookMarksItFailed() {
        PendingPayment pending = new PendingPayment(account, new BigDecimal("200.00"), "USD", "STRIPE", "cs_test_456");
        when(paymentGateway.parseWebhook("payload", "sig"))
                .thenReturn(Optional.of(new PaymentGateway.WebhookResult("cs_test_456", false, "card declined")));
        when(pendingPaymentRepository.findByProviderAndProviderReference("STRIPE", "cs_test_456"))
                .thenReturn(Optional.of(pending));

        service.handleWebhook("payload", "sig");

        assertThat(pending.getStatus()).isEqualTo(PendingPaymentStatus.FAILED);
        assertThat(pending.getFailureReason()).isEqualTo("card declined");
        verify(entryRepository, never()).save(any());
    }

    @Test
    @DisplayName("a redelivered webhook for an already-settled payment is a silent no-op")
    void aRedeliveredWebhookIsANoOp() {
        PendingPayment pending = new PendingPayment(account, new BigDecimal("200.00"), "USD", "STRIPE", "cs_test_789");
        pending.settle(UUID.randomUUID());
        when(paymentGateway.parseWebhook("payload", "sig"))
                .thenReturn(Optional.of(new PaymentGateway.WebhookResult("cs_test_789", true, null)));
        when(pendingPaymentRepository.findByProviderAndProviderReference("STRIPE", "cs_test_789"))
                .thenReturn(Optional.of(pending));

        service.handleWebhook("payload", "sig");

        verify(entryRepository, never()).save(any());
        verify(auditTrail, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a webhook with an invalid signature is a silent no-op")
    void anInvalidSignatureIsANoOp() {
        when(paymentGateway.parseWebhook("payload", "bad-sig")).thenReturn(Optional.empty());

        service.handleWebhook("payload", "bad-sig");

        verify(pendingPaymentRepository, never()).findByProviderAndProviderReference(any(), any());
        verify(entryRepository, never()).save(any());
    }

    @Test
    @DisplayName("a webhook for a reference this system never issued is a silent no-op")
    void anUnknownReferenceIsANoOp() {
        when(paymentGateway.parseWebhook("payload", "sig"))
                .thenReturn(Optional.of(new PaymentGateway.WebhookResult("cs_unknown", true, null)));
        when(pendingPaymentRepository.findByProviderAndProviderReference("STRIPE", "cs_unknown"))
                .thenReturn(Optional.empty());

        service.handleWebhook("payload", "sig");

        verify(entryRepository, never()).save(any());
    }
}
