package com.university.lms.finance.domain;

/** E6: an invoice's lifecycle — deliberately no PARTIALLY_PAID: settling one is a manual staff act, not inferred from the ledger. */
public enum InvoiceStatus {
    ISSUED,
    PAID,
    VOID
}
