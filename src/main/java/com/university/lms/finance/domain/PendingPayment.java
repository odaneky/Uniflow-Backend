package com.university.lms.finance.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * E7: an online payment between "the student clicked pay" and "the gateway confirmed it" — the
 * pending/unsettled state {@link AccountEntry} has no room for, since an entry is only ever
 * written once something has actually posted. Settling one writes the real {@code PAYMENT} entry;
 * until then, nothing on the ledger reflects the attempt at all.
 */
@Entity
@Table(
        name = "pending_payments",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_pending_payments_provider_ref", columnNames = {"provider", "provider_reference"}),
        indexes = @Index(name = "idx_pending_payments_account", columnList = "account_id"))
@Getter
public class PendingPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_pending_payments_account"))
    private StudentAccount account;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PendingPaymentStatus status;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_reference", nullable = false, length = 255)
    private String providerReference;

    @Column(name = "account_entry_id")
    private UUID accountEntryId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "settled_at")
    private Instant settledAt;

    protected PendingPayment() {
        // for JPA
    }

    public PendingPayment(
            StudentAccount account, BigDecimal amount, String currency, String provider, String providerReference) {
        this.account = account;
        this.amount = amount;
        this.currency = currency;
        this.status = PendingPaymentStatus.PENDING;
        this.provider = provider;
        this.providerReference = providerReference;
    }

    public void settle(UUID accountEntryId) {
        requirePending();
        this.status = PendingPaymentStatus.SETTLED;
        this.accountEntryId = accountEntryId;
        this.settledAt = Instant.now();
    }

    public void fail(String reason) {
        requirePending();
        this.status = PendingPaymentStatus.FAILED;
        this.failureReason = reason;
    }

    private void requirePending() {
        if (status != PendingPaymentStatus.PENDING) {
            throw new IllegalStateException("This payment was already " + status.name().toLowerCase());
        }
    }
}
