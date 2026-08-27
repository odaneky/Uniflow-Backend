package com.university.lms.finance.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/**
 * E6: a billable document — a frozen snapshot of a term's charges, distinct from the running
 * ledger {@link AccountEntry} rows post to continuously. Printable, emailable, and (via {@link
 * #billToName}/{@link #billToEmail}) addressable to a third-party sponsor instead of the student.
 *
 * <p>Its total is fixed at issue time and never recomputed — a later correction to the underlying
 * entries must not silently change an invoice that may already have been sent out.
 */
@Entity
@Table(
        name = "invoices",
        indexes = @Index(name = "idx_invoices_student", columnList = "student_id,academic_term_id"))
@Getter
public class Invoice extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "academic_term_id", nullable = false)
    private UUID academicTermId;

    @Column(name = "invoice_number", nullable = false, length = 20)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    /** Null: the student is billed directly. Set: a third party — E6's sponsor-billing half. */
    @Column(name = "bill_to_name", length = 200)
    private String billToName;

    @Column(name = "bill_to_email", length = 255)
    private String billToEmail;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    protected Invoice() {
        // for JPA
    }

    public Invoice(
            UUID studentId,
            UUID academicTermId,
            String invoiceNumber,
            BigDecimal totalAmount,
            String currency,
            LocalDate dueOn,
            String billToName,
            String billToEmail,
            String notes) {
        this.studentId = studentId;
        this.academicTermId = academicTermId;
        this.invoiceNumber = invoiceNumber;
        this.status = InvoiceStatus.ISSUED;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.issuedAt = Instant.now();
        this.dueOn = dueOn;
        this.billToName = billToName;
        this.billToEmail = billToEmail;
        this.notes = notes;
    }

    public void markPaid() {
        if (status != InvoiceStatus.ISSUED) {
            throw new IllegalStateException("Only an issued invoice can be marked paid — this one is " + status);
        }
        this.status = InvoiceStatus.PAID;
        this.paidAt = Instant.now();
    }

    /** Correcting a mistake is still allowed after payment; voiding an already-void invoice is not. */
    public void voidInvoice(String reason) {
        if (status == InvoiceStatus.VOID) {
            throw new IllegalStateException("This invoice is already void");
        }
        this.status = InvoiceStatus.VOID;
        this.voidedAt = Instant.now();
        this.voidReason = reason;
    }

    /** E6: aging — zero unless the invoice is still outstanding and past its due date. */
    public long daysOverdue(LocalDate asOf) {
        if (status != InvoiceStatus.ISSUED || !dueOn.isBefore(asOf)) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(dueOn, asOf);
    }

    public boolean isOverdue(LocalDate asOf) {
        return daysOverdue(asOf) > 0;
    }
}
