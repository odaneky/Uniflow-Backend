package com.university.lms.finance.service;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.administration.api.RecordAccessLog;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.finance.api.PaymentStanding;
import com.university.lms.finance.config.FinanceProperties;
import com.university.lms.finance.domain.AccountEntry;
import com.university.lms.finance.domain.AccountEntryType;
import com.university.lms.finance.domain.FinanceErrorCode;
import com.university.lms.finance.domain.StudentAccount;
import com.university.lms.finance.dto.AccountEntryResponse;
import com.university.lms.finance.dto.AccountResponse;
import com.university.lms.finance.dto.CreateAccountEntryRequest;
import com.university.lms.finance.dto.CreatePaymentRequest;
import com.university.lms.finance.dto.PaymentResponse;
import com.university.lms.finance.repository.AccountEntryRepository;
import com.university.lms.finance.repository.StudentAccountRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FinanceService {

    private final StudentAccountRepository accountRepository;
    private final AccountEntryRepository entryRepository;
    private final StudentDirectory studentDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final PaymentPlanService paymentPlanService;
    private final FinanceProperties financeProperties;
    private final RecordAccessLog recordAccessLog;
    private final AuditTrail auditTrail;

    public FinanceService(
            StudentAccountRepository accountRepository,
            AccountEntryRepository entryRepository,
            StudentDirectory studentDirectory,
            CurrentUserProvider currentUserProvider,
            PaymentPlanService paymentPlanService,
            FinanceProperties financeProperties,
            RecordAccessLog recordAccessLog,
            AuditTrail auditTrail) {
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
        this.studentDirectory = studentDirectory;
        this.currentUserProvider = currentUserProvider;
        this.paymentPlanService = paymentPlanService;
        this.financeProperties = financeProperties;
        this.recordAccessLog = recordAccessLog;
        this.auditTrail = auditTrail;
    }

    public AccountResponse own() {
        CurrentUser caller = currentUserProvider.require();
        UUID studentId = studentDirectory
                .studentIdOfUser(caller.userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
        return accountRepository
                .findByStudentId(studentId)
                .map(this::toResponse)
                .orElseGet(() -> emptyAccount(studentId));
    }

    public AccountResponse forStudent(UUID studentId) {
        CurrentUser caller = requireRegistry();
        if (!studentDirectory.exists(studentId)) {
            throw new ResourceNotFoundException(
                    FinanceErrorCode.ACCOUNT_STUDENT_NOT_FOUND, "No student exists with id " + studentId);
        }
        recordAccessLog.record(
                caller.userId(),
                caller.fullName(),
                studentId,
                RecordAccessLog.RecordType.FINANCE,
                RecordAccessLog.Action.VIEW,
                "Student account");
        return accountRepository
                .findByStudentId(studentId)
                .map(this::toResponse)
                .orElseGet(() -> emptyAccount(studentId));
    }

    @Transactional
    public AccountResponse addEntry(UUID studentId, CreateAccountEntryRequest request) {
        CurrentUser caller = requireRegistry();
        if (!studentDirectory.exists(studentId)) {
            throw new ResourceNotFoundException(
                    FinanceErrorCode.ACCOUNT_STUDENT_NOT_FOUND, "No student exists with id " + studentId);
        }
        StudentAccount account = accountRepository
                .findByStudentId(studentId)
                .orElseGet(() -> accountRepository.save(
                        new StudentAccount(studentId, request.currency() == null ? "USD" : request.currency())));
        if (request.dueOn() != null) {
            account.dueOn(request.dueOn());
        }
        BigDecimal signed = signedAmount(request.entryType(), request.amount());
        AccountEntry entry = entryRepository.save(new AccountEntry(
                account,
                request.entryType(),
                signed,
                request.description(),
                request.occurredAt() == null ? Instant.now() : request.occurredAt()));
        recordLedgerEntryAudit(caller, studentId, entry, signed);
        return toResponse(account);
    }

    /**
     * A manual ledger entry posts an arbitrary signed amount with no second-person approval — the
     * full-form record with a reason and an after-snapshot is exactly what
     * {@link AuditTrail#record(UUID, String, String, String, UUID, String, String, String, String)}
     * was written for.
     */
    private void recordLedgerEntryAudit(CurrentUser caller, UUID studentId, AccountEntry entry, BigDecimal signed) {
        String afterValue = "{\"studentId\":\"" + studentId + "\",\"entryType\":\"" + entry.getEntryType()
                + "\",\"amount\":\"" + signed + "\",\"description\":\"" + entry.getDescription().replace("\"", "'")
                + "\"}";
        auditTrail.record(
                caller.userId(),
                caller.fullName(),
                AuditTrail.Action.LEDGER_ENTRY_POSTED,
                AuditTrail.EntityType.ACCOUNT_ENTRY,
                entry.getId(),
                "Manual " + entry.getEntryType() + " of " + signed.abs() + " on student " + studentId,
                entry.getDescription(),
                null,
                afterValue);
    }

    /**
     * Campus cashier stub: posts a PAYMENT against the caller's own ledger. No card network, no
     * PAN. Amount may not exceed what is currently owed.
     */
    @Transactional
    public PaymentResponse payOwn(CreatePaymentRequest request) {
        if (!financeProperties.selfServicePaymentEnabled()) {
            throw new BusinessException(
                    FinanceErrorCode.SELF_SERVICE_PAYMENT_DISABLED,
                    "Self-service payment is not available. Please contact the bursar's office to make a payment.");
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
        BigDecimal balance = balanceOf(account);
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    FinanceErrorCode.PAYMENT_NOTHING_DUE, "There is nothing due on this account");
        }
        if (request.amount().compareTo(balance) > 0) {
            throw new BusinessException(
                    FinanceErrorCode.PAYMENT_EXCEEDS_BALANCE,
                    "Payment cannot exceed the outstanding balance of " + balance);
        }
        Instant at = Instant.now();
        AccountEntry entry = entryRepository.save(new AccountEntry(
                account, AccountEntryType.PAYMENT, request.amount().negate(), "Campus cashier payment", at));
        return new PaymentResponse(
                entry.getId(), request.amount(), balance.subtract(request.amount()), account.getCurrency(), at);
    }

    private AccountResponse toResponse(StudentAccount account) {
        List<AccountEntry> entries = entryRepository.findByAccountIdOrderByOccurredAtAsc(account.getId());
        BigDecimal balance = entries.stream().map(AccountEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AccountResponse(
                account.getId(),
                account.getStudentId(),
                account.getCurrency(),
                balance,
                account.getDueOn(),
                entries.stream().map(AccountEntryResponse::from).toList(),
                standingOf(account.getStudentId()));
    }

    private AccountResponse emptyAccount(UUID studentId) {
        return new AccountResponse(
                null, studentId, "USD", BigDecimal.ZERO, null, List.of(), standingOf(studentId));
    }

    private PaymentStanding standingOf(UUID studentId) {
        return paymentPlanService.standingOf(studentId, null, LocalDate.now(ZoneOffset.UTC));
    }

    private BigDecimal balanceOf(StudentAccount account) {
        return entryRepository.findByAccountIdOrderByOccurredAtAsc(account.getId()).stream()
                .map(AccountEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal signedAmount(AccountEntryType type, BigDecimal amount) {
        return type == AccountEntryType.CHARGE ? amount : amount.negate();
    }

    /**
     * A6 groundwork: widened to also accept {@code BURSAR}, the eventual owner of the ledger.
     * {@code REGISTRAR} keeps everything it has today — nobody has been granted {@code BURSAR} in
     * any real environment yet, so narrowing this to exclude {@code REGISTRAR} would lock out
     * every real registrar the day it shipped.
     */
    private CurrentUser requireRegistry() {
        CurrentUser caller = currentUserProvider.require();
        if (!(caller.hasRole(SecurityRoles.SYSTEM_ADMIN)
                || caller.hasRole(SecurityRoles.REGISTRAR)
                || caller.hasRole(SecurityRoles.BURSAR))) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
        return caller;
    }
}
