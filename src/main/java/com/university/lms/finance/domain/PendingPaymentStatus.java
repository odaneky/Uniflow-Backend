package com.university.lms.finance.domain;

/** E7: the pending/unsettled state the ledger otherwise lacks — an online payment in flight. */
public enum PendingPaymentStatus {
    PENDING,
    SETTLED,
    FAILED
}
