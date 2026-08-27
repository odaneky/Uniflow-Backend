package com.university.lms.finance.service;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.finance.domain.AccountEntry;
import com.university.lms.finance.domain.AccountEntryStatus;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * E7: online payment via a hosted-checkout {@link PaymentGateway}. Two steps, split precisely at
 * the point where money changes hands: {@link #initiate} only ever creates a {@link
 * PendingPayment} and hands back a redirect URL — nothing posts to the ledger yet. {@link
 * #handleWebhook} is the only place a real {@code PAYMENT} entry gets written, and only once the
 * provider itself confirms it, server-to-server, out of band from whatever the browser reports.
 *
 * <p>Distinct from {@code FinanceService.payOwn}, the campus-cashier stub that posts a real
 * payment with no gateway behind it at all — the two are independently gated (self-service
 * payment's own flag; this by whether a real {@link PaymentGateway} is configured) and meant for
 * different deployments, not a fallback chain between them.
 */
@Service
@Transactional(readOnly = true)
public class OnlinePaymentService {

    private static final Logger log = LoggerFactory.getLogger(OnlinePaymentService.class);
    private static final String PROVIDER = "STRIPE";

    private final PendingPaymentRepository pendingPaymentRepository;
    private final AccountEntryRepository entryRepository;
    private final StudentAccountRepository accountRepository;
    private final StudentDirectory studentDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final PaymentGateway paymentGateway;
    private final AuditTrail auditTrail;

    public OnlinePaymentService(
            PendingPaymentRepository pendingPaymentRepository,
            AccountEntryRepository entryRepository,
            StudentAccountRepository accountRepository,
            StudentDirectory studentDirectory,
            CurrentUserProvider currentUserProvider,
            PaymentGateway paymentGateway,
            AuditTrail auditTrail) {
        this.pendingPaymentRepository = pendingPaymentRepository;
        this.entryRepository = entryRepository;
        this.accountRepository = accountRepository;
        this.studentDirectory = studentDirectory;
        this.currentUserProvider = currentUserProvider;
        this.paymentGateway = paymentGateway;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public OnlinePaymentResponse initiate(CreatePaymentRequest request) {
        if (!paymentGateway.configured()) {
            throw new BusinessException(
                    FinanceErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED,
                    "Online payment is not available. Please contact the bursar's office to make a payment.");
        }
        CurrentUser caller = currentUserProvider.require();
        UUID studentId = studentDirectory
                .studentIdOfUser(caller.userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
        StudentAccount account = accountRepository
                .lockByStudentId(studentId)
                .orElseThrow(() -> new BusinessException(
                        FinanceErrorCode.PAYMENT_NOTHING_DUE, "There is nothing due on this account"));
        BigDecimal balance = postedBalance(account.getId());
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    FinanceErrorCode.PAYMENT_NOTHING_DUE, "There is nothing due on this account");
        }
        if (request.amount().compareTo(balance) > 0) {
            throw new BusinessException(
                    FinanceErrorCode.PAYMENT_EXCEEDS_BALANCE,
                    "Payment cannot exceed the outstanding balance of " + balance);
        }

        PaymentGateway.CheckoutSession session;
        try {
            session = paymentGateway.createCheckoutSession(UUID.randomUUID(), request.amount(), account.getCurrency());
        } catch (RuntimeException ex) {
            log.error("Payment gateway checkout session creation failed for student {}", studentId, ex);
            throw new BusinessException(
                    FinanceErrorCode.PAYMENT_GATEWAY_ERROR, "Could not start an online payment right now");
        }

        PendingPayment pending = pendingPaymentRepository.save(
                new PendingPayment(account, request.amount(), account.getCurrency(), PROVIDER, session.providerReference()));
        auditTrail.record(
                caller.userId(),
                caller.fullName(),
                AuditTrail.Action.ONLINE_PAYMENT_INITIATED,
                AuditTrail.EntityType.PENDING_PAYMENT,
                pending.getId(),
                "Initiated " + request.amount() + " " + account.getCurrency() + " via " + PROVIDER);
        return new OnlinePaymentResponse(pending.getId(), session.redirectUrl());
    }

    /**
     * Reconciles a provider webhook. Deliberately silent on every "nothing to do here" path — an
     * invalid signature, an event for a reference this system never issued, a redelivery of one
     * already settled — since a webhook endpoint that reveals which of those happened via its
     * response is handing an attacker a signature oracle. The provider only needs to know its
     * request was received; whether anything downstream of that has already happened is between
     * this system and its own ledger.
     */
    @Transactional
    public void handleWebhook(String payloadJson, String signatureHeader) {
        Optional<PaymentGateway.WebhookResult> result = paymentGateway.parseWebhook(payloadJson, signatureHeader);
        if (result.isEmpty()) {
            return;
        }
        PaymentGateway.WebhookResult event = result.get();
        Optional<PendingPayment> maybePending =
                pendingPaymentRepository.findByProviderAndProviderReference(PROVIDER, event.providerReference());
        if (maybePending.isEmpty()) {
            log.warn("Webhook for unknown pending payment reference {}", event.providerReference());
            return;
        }
        PendingPayment pending = maybePending.get();
        if (pending.getStatus() != PendingPaymentStatus.PENDING) {
            return;
        }
        if (event.succeeded()) {
            AccountEntry entry = entryRepository.save(new AccountEntry(
                    pending.getAccount(),
                    AccountEntryType.PAYMENT,
                    pending.getAmount().negate(),
                    "Online payment",
                    Instant.now()));
            pending.settle(entry.getId());
            pendingPaymentRepository.save(pending);
            auditTrail.record(
                    null,
                    "Stripe webhook",
                    AuditTrail.Action.ONLINE_PAYMENT_SETTLED,
                    AuditTrail.EntityType.PENDING_PAYMENT,
                    pending.getId(),
                    "Settled " + pending.getAmount() + " " + pending.getCurrency());
        } else {
            pending.fail(event.failureReason());
            pendingPaymentRepository.save(pending);
            auditTrail.record(
                    null,
                    "Stripe webhook",
                    AuditTrail.Action.ONLINE_PAYMENT_FAILED,
                    AuditTrail.EntityType.PENDING_PAYMENT,
                    pending.getId(),
                    event.failureReason());
        }
    }

    private BigDecimal postedBalance(UUID accountId) {
        List<AccountEntry> entries = entryRepository.findByAccountIdOrderByOccurredAtAsc(accountId);
        return entries.stream()
                .filter(entry -> entry.getStatus() == AccountEntryStatus.POSTED)
                .map(AccountEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
