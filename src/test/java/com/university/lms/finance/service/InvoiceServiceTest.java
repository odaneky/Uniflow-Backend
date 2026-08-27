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
import com.university.lms.finance.domain.AccountEntryStatus;
import com.university.lms.finance.domain.AccountEntryType;
import com.university.lms.finance.domain.FinanceErrorCode;
import com.university.lms.finance.domain.Invoice;
import com.university.lms.finance.domain.InvoiceStatus;
import com.university.lms.finance.domain.StudentAccount;
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
import java.time.Instant;
import java.time.LocalDate;
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
class InvoiceServiceTest {

    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID TERM_ID = UUID.randomUUID();
    private static final UUID REGISTRAR_USER_ID = UUID.randomUUID();
    private static final CurrentUser REGISTRAR = new CurrentUser(
            REGISTRAR_USER_ID,
            "sub-registrar",
            "registrar",
            "registrar@university.test",
            "Rita Registrar",
            Optional.empty(),
            Set.of(SecurityRoles.REGISTRAR),
            Set.of());
    private static final CurrentUser STUDENT = new CurrentUser(
            UUID.randomUUID(),
            "sub-student",
            "202099999",
            "student@university.test",
            "Sam Student",
            Optional.of("202099999"),
            Set.of(SecurityRoles.STUDENT),
            Set.of());

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceLineItemRepository lineItemRepository;

    @Mock
    private AccountEntryRepository entryRepository;

    @Mock
    private StudentAccountRepository accountRepository;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private AuditTrail auditTrail;

    private InvoiceService service;

    private StudentAccount account;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(
                invoiceRepository,
                lineItemRepository,
                entryRepository,
                accountRepository,
                studentDirectory,
                currentUserProvider,
                auditTrail);

        account = new StudentAccount(STUDENT_ID, "USD");
        lenient().when(studentDirectory.exists(STUDENT_ID)).thenReturn(true);
        lenient().when(accountRepository.findByStudentId(STUDENT_ID)).thenReturn(Optional.of(account));
        lenient().when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient()
                .when(lineItemRepository.findByInvoiceIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
    }

    private AccountEntry charge(BigDecimal amount, String description) {
        return new AccountEntry(account, AccountEntryType.CHARGE, amount, description, Instant.now(), null, TERM_ID);
    }

    @Test
    @DisplayName("issuing bundles a term's posted charges into one frozen total")
    void issuingBundlesPostedCharges() {
        when(currentUserProvider.require()).thenReturn(REGISTRAR);
        when(entryRepository.findByAccountIdAndAcademicTermIdAndEntryTypeAndStatusOrderByOccurredAtAsc(
                        account.getId(), TERM_ID, AccountEntryType.CHARGE, AccountEntryStatus.POSTED))
                .thenReturn(List.of(charge(new BigDecimal("1200.00"), "Tuition"), charge(new BigDecimal("50.00"), "Campus fee")));

        InvoiceResponse response = service.issue(
                STUDENT_ID, new IssueInvoiceRequest(TERM_ID, LocalDate.now().plusDays(30), null, null, null));

        assertThat(response.totalAmount()).isEqualByComparingTo("1250.00");
        assertThat(response.status()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(response.invoiceNumber()).startsWith("INV-");
        verify(lineItemRepository, org.mockito.Mockito.times(2)).save(any());
        verify(auditTrail)
                .record(
                        org.mockito.ArgumentMatchers.eq(REGISTRAR_USER_ID),
                        org.mockito.ArgumentMatchers.eq("Rita Registrar"),
                        org.mockito.ArgumentMatchers.eq(AuditTrail.Action.INVOICE_ISSUED),
                        org.mockito.ArgumentMatchers.eq(AuditTrail.EntityType.INVOICE),
                        any(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("issuing with no posted charges for the term is refused")
    void issuingWithNoChargesIsRefused() {
        when(currentUserProvider.require()).thenReturn(REGISTRAR);
        when(entryRepository.findByAccountIdAndAcademicTermIdAndEntryTypeAndStatusOrderByOccurredAtAsc(
                        account.getId(), TERM_ID, AccountEntryType.CHARGE, AccountEntryStatus.POSTED))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.issue(
                        STUDENT_ID, new IssueInvoiceRequest(TERM_ID, LocalDate.now().plusDays(30), null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(FinanceErrorCode.INVOICE_NO_CHARGES));
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("a sponsor-billed invoice carries the bill-to name and email")
    void sponsorBilledInvoiceCarriesBillTo() {
        when(currentUserProvider.require()).thenReturn(REGISTRAR);
        when(entryRepository.findByAccountIdAndAcademicTermIdAndEntryTypeAndStatusOrderByOccurredAtAsc(
                        account.getId(), TERM_ID, AccountEntryType.CHARGE, AccountEntryStatus.POSTED))
                .thenReturn(List.of(charge(new BigDecimal("800.00"), "Tuition")));

        InvoiceResponse response = service.issue(
                STUDENT_ID,
                new IssueInvoiceRequest(
                        TERM_ID, LocalDate.now().plusDays(30), "Acme Sponsorship Trust", "billing@acme.test", null));

        assertThat(response.billToName()).isEqualTo("Acme Sponsorship Trust");
        assertThat(response.billToEmail()).isEqualTo("billing@acme.test");
    }

    @Test
    @DisplayName("an issued invoice past its due date is reported as overdue")
    void anOverdueInvoiceIsFlagged() {
        Invoice invoice = new Invoice(
                STUDENT_ID, TERM_ID, "INV-00001", new BigDecimal("500.00"), "USD",
                LocalDate.now().minusDays(10), null, null, null);
        when(currentUserProvider.require()).thenReturn(REGISTRAR);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        InvoiceResponse response = service.find(invoice.getId());

        assertThat(response.overdue()).isTrue();
        assertThat(response.daysOverdue()).isEqualTo(10);
    }

    @Test
    @DisplayName("marking paid twice is refused")
    void markingPaidTwiceIsRefused() {
        Invoice invoice = new Invoice(
                STUDENT_ID, TERM_ID, "INV-00002", new BigDecimal("500.00"), "USD",
                LocalDate.now().plusDays(30), null, null, null);
        invoice.markPaid();
        when(currentUserProvider.require()).thenReturn(REGISTRAR);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.markPaid(invoice.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(FinanceErrorCode.INVOICE_ALREADY_DECIDED));
    }

    @Test
    @DisplayName("voiding an already-void invoice is refused")
    void voidingAnAlreadyVoidInvoiceIsRefused() {
        Invoice invoice = new Invoice(
                STUDENT_ID, TERM_ID, "INV-00003", new BigDecimal("500.00"), "USD",
                LocalDate.now().plusDays(30), null, null, null);
        invoice.voidInvoice("Mistake");
        when(currentUserProvider.require()).thenReturn(REGISTRAR);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.voidInvoice(invoice.getId(), "Again"))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(FinanceErrorCode.INVOICE_ALREADY_DECIDED));
    }

    @Test
    @DisplayName("a student cannot issue an invoice")
    void aStudentCannotIssueAnInvoice() {
        when(currentUserProvider.require()).thenReturn(STUDENT);

        assertThatThrownBy(() -> service.issue(
                        STUDENT_ID, new IssueInvoiceRequest(TERM_ID, LocalDate.now().plusDays(30), null, null, null)))
                .isInstanceOf(com.university.lms.common.exception.ForbiddenException.class);
    }
}
