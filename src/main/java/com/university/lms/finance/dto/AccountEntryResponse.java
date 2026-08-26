package com.university.lms.finance.dto;

import com.university.lms.finance.domain.AccountEntry;
import com.university.lms.finance.domain.AccountEntryStatus;
import com.university.lms.finance.domain.AccountEntryType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountEntryResponse(
        UUID id,
        AccountEntryType entryType,
        BigDecimal amount,
        String description,
        Instant occurredAt,
        String reference,
        AccountEntryStatus status,
        UUID proposedBy,
        UUID decidedBy,
        Instant decidedAt,
        String decisionNote) {

    public static AccountEntryResponse from(AccountEntry entry) {
        return new AccountEntryResponse(
                entry.getId(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getDescription(),
                entry.getOccurredAt(),
                entry.getReference(),
                entry.getStatus(),
                entry.getProposedBy(),
                entry.getDecidedBy(),
                entry.getDecidedAt(),
                entry.getDecisionNote());
    }
}
