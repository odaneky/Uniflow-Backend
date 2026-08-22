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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "account_entries", indexes = @Index(name = "idx_account_entries_account", columnList = "account_id"))
@Getter
public class AccountEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_account_entries_account"))
    private StudentAccount account;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private AccountEntryType entryType;

    /** Signed: charges increase what is owed; payments and credits decrease it. */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "reference", length = 120)
    private String reference;

    protected AccountEntry() {}

    public AccountEntry(
            StudentAccount account,
            AccountEntryType entryType,
            BigDecimal amount,
            String description,
            Instant occurredAt) {
        this(account, entryType, amount, description, occurredAt, null);
    }

    public AccountEntry(
            StudentAccount account,
            AccountEntryType entryType,
            BigDecimal amount,
            String description,
            Instant occurredAt,
            String reference) {
        this.account = account;
        this.entryType = entryType;
        this.amount = amount;
        this.description = description;
        this.occurredAt = occurredAt;
        this.reference = reference;
    }
}
