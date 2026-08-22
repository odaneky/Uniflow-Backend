package com.university.lms.financialaid.domain;

import com.university.lms.common.exception.ErrorCode;

public enum FinancialAidErrorCode implements ErrorCode {
    ISIR_STUDENT_NOT_FOUND,
    ISIR_INVALID_ROW,
    AWARD_NOT_FOUND,
    AWARD_INVALID_STATE,
    AWARD_ALREADY_DISBURSED,
    HOLD_NOT_FOUND,
    HOLD_ALREADY_CLEARED,
    SAP_EVALUATION_NOT_FOUND,
    STUDENT_NOT_FOUND;

    @Override
    public String code() {
        return name();
    }
}
