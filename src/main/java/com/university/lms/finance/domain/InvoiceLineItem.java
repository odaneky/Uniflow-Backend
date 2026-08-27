package com.university.lms.finance.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

/** E6: one charge as it appeared on an invoice at issue time — snapshotted, not a live reference. */
@Entity
@Table(name = "invoice_line_items", indexes = @Index(name = "idx_invoice_line_items_invoice", columnList = "invoice_id"))
@Getter
public class InvoiceLineItem extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    protected InvoiceLineItem() {
        // for JPA
    }

    public InvoiceLineItem(UUID invoiceId, String description, BigDecimal amount) {
        this.invoiceId = invoiceId;
        this.description = description;
        this.amount = amount;
    }
}
