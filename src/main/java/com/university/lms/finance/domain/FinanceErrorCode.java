package com.university.lms.finance.domain;

import com.university.lms.common.exception.ErrorCode;

public enum FinanceErrorCode implements ErrorCode {
    ACCOUNT_NOT_FOUND,
    ACCOUNT_STUDENT_NOT_FOUND,
    PAYMENT_NOTHING_DUE,
    PAYMENT_EXCEEDS_BALANCE,
    SELF_SERVICE_PAYMENT_DISABLED,
    PAYMENT_PLAN_TERM_NOT_FOUND,
    INVALID_PAYMENT_PLAN,
    TUITION_PROGRAMME_NOT_FOUND,
    FEE_NOT_FOUND,
    FEE_NAME_ALREADY_EXISTS,
    INVALID_FEE,
    INVALID_REFUND_POLICY,
    LEDGER_ENTRY_NOT_FOUND,
    LEDGER_ENTRY_ALREADY_DECIDED,
    LEDGER_ENTRY_SELF_APPROVAL,
    INVOICE_NOT_FOUND,
    INVOICE_NO_CHARGES,
    INVOICE_ALREADY_DECIDED;

    @Override
    public String code() {
        return name();
    }
}
