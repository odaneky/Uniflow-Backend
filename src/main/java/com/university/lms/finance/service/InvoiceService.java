package com.university.lms.finance.service;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.finance.domain.AccountEntry;
import com.university.lms.finance.domain.AccountEntryStatus;
import com.university.lms.finance.domain.AccountEntryType;
import com.university.lms.finance.domain.FinanceErrorCode;
import com.university.lms.finance.domain.Invoice;
import com.university.lms.finance.domain.InvoiceLineItem;
import com.university.lms.finance.domain.StudentAccount;
import com.university.lms.finance.dto.InvoiceLineItemResponse;
import com.university.lms.finance.dto.InvoiceResponse;
import com.university.lms.finance.dto.IssueInvoiceRequest;
import com.university.lms.finance.repository.AccountEntryRepository;
import com.university.lms.finance.repository.InvoiceLineItemRepository;
import com.university.lms.finance.repository.InvoiceRepository;
import com.university.lms.finance.repository.StudentAccountRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * E6: invoices — a billable document distinct from the running ledger {@link AccountEntry} rows
 * post to continuously. An invoice is a frozen snapshot of a term's posted charges, addressable to
 * the student or, via {@code billToName}/{@code billToEmail}, to a third-party sponsor.
 *
 * <p>Deliberately out of scope here: automated dunning (scheduled overdue reminders — an operational
 * job, not a domain concern this service owns) and payment-waterfall reconciliation (which specific
 * payment settles which invoice). Settling one is a manual staff act via {@link #markPaid}; {@code
 * daysOverdue} on the response is the aging signal a future dunning job would read.
 */
@Service
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository lineItemRepository;
    private final AccountEntryRepository entryRepository;
    private final StudentAccountRepository accountRepository;
    private final StudentDirectory studentDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final AuditTrail auditTrail;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            InvoiceLineItemRepository lineItemRepository,
            AccountEntryRepository entryRepository,
            StudentAccountRepository accountRepository,
            StudentDirectory studentDirectory,
            CurrentUserProvider currentUserProvider,
            AuditTrail auditTrail) {
        this.invoiceRepository = invoiceRepository;
        this.lineItemRepository = lineItemRepository;
        this.entryRepository = entryRepository;
        this.accountRepository = accountRepository;
        this.studentDirectory = studentDirectory;
        this.currentUserProvider = currentUserProvider;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public InvoiceResponse issue(UUID studentId, IssueInvoiceRequest request) {
        CurrentUser caller = requireRegistry();
        if (!studentDirectory.exists(studentId)) {
            throw new ResourceNotFoundException(
                    FinanceErrorCode.ACCOUNT_STUDENT_NOT_FOUND, "No student exists with id " + studentId);
        }
        StudentAccount account = accountRepository
                .findByStudentId(studentId)
                .orElseThrow(() -> new BusinessException(
                        FinanceErrorCode.INVOICE_NO_CHARGES, "This student has no charges to invoice"));
        List<AccountEntry> charges = entryRepository
                .findByAccountIdAndAcademicTermIdAndEntryTypeAndStatusOrderByOccurredAtAsc(
                        account.getId(), request.academicTermId(), AccountEntryType.CHARGE, AccountEntryStatus.POSTED);
        if (charges.isEmpty()) {
            throw new BusinessException(
                    FinanceErrorCode.INVOICE_NO_CHARGES, "No posted charges exist for this student in that term");
        }
        BigDecimal total = charges.stream().map(AccountEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Invoice invoice = invoiceRepository.save(new Invoice(
                studentId,
                request.academicTermId(),
                nextInvoiceNumber(),
                total,
                account.getCurrency(),
                request.dueOn(),
                blankToNull(request.billToName()),
                blankToNull(request.billToEmail()),
                blankToNull(request.notes())));
        for (AccountEntry charge : charges) {
            lineItemRepository.save(new InvoiceLineItem(invoice.getId(), charge.getDescription(), charge.getAmount()));
        }

        auditTrail.record(
                caller.userId(),
                caller.fullName(),
                AuditTrail.Action.INVOICE_ISSUED,
                AuditTrail.EntityType.INVOICE,
                invoice.getId(),
                "Invoice " + invoice.getInvoiceNumber() + " for " + total + " " + account.getCurrency()
                        + " on student " + studentId);
        return toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse markPaid(UUID invoiceId) {
        CurrentUser caller = requireRegistry();
        Invoice invoice = require(invoiceId);
        try {
            invoice.markPaid();
        } catch (IllegalStateException ex) {
            throw new BusinessException(FinanceErrorCode.INVOICE_ALREADY_DECIDED, ex.getMessage());
        }
        invoiceRepository.save(invoice);
        auditTrail.record(
                caller.userId(),
                caller.fullName(),
                AuditTrail.Action.INVOICE_MARKED_PAID,
                AuditTrail.EntityType.INVOICE,
                invoice.getId(),
                "Invoice " + invoice.getInvoiceNumber());
        return toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse voidInvoice(UUID invoiceId, String reason) {
        CurrentUser caller = requireRegistry();
        Invoice invoice = require(invoiceId);
        try {
            invoice.voidInvoice(reason);
        } catch (IllegalStateException ex) {
            throw new BusinessException(FinanceErrorCode.INVOICE_ALREADY_DECIDED, ex.getMessage());
        }
        invoiceRepository.save(invoice);
        auditTrail.record(
                caller.userId(),
                caller.fullName(),
                AuditTrail.Action.INVOICE_VOIDED,
                AuditTrail.EntityType.INVOICE,
                invoice.getId(),
                "Invoice " + invoice.getInvoiceNumber() + ": " + reason);
        return toResponse(invoice);
    }

    public InvoiceResponse find(UUID invoiceId) {
        requireRegistry();
        return toResponse(require(invoiceId));
    }

    public List<InvoiceResponse> forStudent(UUID studentId) {
        requireRegistry();
        if (!studentDirectory.exists(studentId)) {
            throw new ResourceNotFoundException(
                    FinanceErrorCode.ACCOUNT_STUDENT_NOT_FOUND, "No student exists with id " + studentId);
        }
        return invoiceRepository.findByStudentIdOrderByIssuedAtDesc(studentId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<InvoiceResponse> own() {
        CurrentUser caller = currentUserProvider.require();
        UUID studentId = studentDirectory
                .studentIdOfUser(caller.userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
        return invoiceRepository.findByStudentIdOrderByIssuedAtDesc(studentId).stream()
                .map(this::toResponse)
                .toList();
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        List<InvoiceLineItemResponse> lineItems = lineItemRepository.findByInvoiceIdOrderByCreatedAtAsc(invoice.getId())
                .stream()
                .map(InvoiceLineItemResponse::from)
                .toList();
        return InvoiceResponse.from(invoice, lineItems, LocalDate.now());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nextInvoiceNumber() {
        for (int i = 0; i < 8; i++) {
            String candidate = "INV-" + String.format("%05d", Math.floorMod(UUID.randomUUID().hashCode(), 100_000));
            if (!invoiceRepository.existsByInvoiceNumber(candidate)) {
                return candidate;
            }
        }
        return "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Invoice require(UUID invoiceId) {
        return invoiceRepository
                .findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        FinanceErrorCode.INVOICE_NOT_FOUND, "No invoice exists with id " + invoiceId));
    }

    /** A6 groundwork: widened to also accept {@code BURSAR}, mirroring {@code FinanceService}'s own guard. */
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
