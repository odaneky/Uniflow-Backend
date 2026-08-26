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
import java.util.UUID;
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

    @Column(name = "academic_term_id")
    private UUID academicTermId;

    /** E3: PENDING only for a manually proposed entry awaiting a second staff member's decision. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountEntryStatus status = AccountEntryStatus.POSTED;

    @Column(name = "proposed_by")
    private UUID proposedBy;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

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
        this(account, entryType, amount, description, occurredAt, reference, null);
    }

    public AccountEntry(
            StudentAccount account,
            AccountEntryType entryType,
            BigDecimal amount,
            String description,
            Instant occurredAt,
            String reference,
            UUID academicTermId) {
        this.account = account;
        this.entryType = entryType;
        this.amount = amount;
        this.description = description;
        this.occurredAt = occurredAt;
        this.reference = reference;
        this.academicTermId = academicTermId;
    }

    /** A manually proposed entry: excluded from the balance until a different staff member decides it. */
    public static AccountEntry propose(
            StudentAccount account,
            AccountEntryType entryType,
            BigDecimal amount,
            String description,
            Instant occurredAt,
            UUID proposedBy) {
        AccountEntry entry = new AccountEntry(account, entryType, amount, description, occurredAt);
        entry.status = AccountEntryStatus.PENDING;
        entry.proposedBy = proposedBy;
        return entry;
    }

    public void approve(UUID decidedBy) {
        requirePending();
        this.status = AccountEntryStatus.POSTED;
        this.decidedBy = decidedBy;
        this.decidedAt = Instant.now();
    }

    public void reject(UUID decidedBy, String reason) {
        requirePending();
        this.status = AccountEntryStatus.REJECTED;
        this.decidedBy = decidedBy;
        this.decidedAt = Instant.now();
        this.decisionNote = reason;
    }

    private void requirePending() {
        if (status != AccountEntryStatus.PENDING) {
            throw new IllegalStateException("This entry was already " + status.name().toLowerCase());
        }
    }
}
