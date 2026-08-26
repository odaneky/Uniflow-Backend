package com.university.lms.finance.domain;

/** E3: a manual ledger entry's approval state — automated postings skip straight to POSTED. */
public enum AccountEntryStatus {
    PENDING,
    POSTED,
    REJECTED
}
