package com.university.lms.enrollment.dto;

import com.university.lms.academic.api.AcademicStructure.CreditLoad;
import com.university.lms.finance.api.StudentBilling.CatalogFeeQuote;
import com.university.lms.finance.api.StudentBilling.TuitionQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The caller's current registration cycle: which term applies, which actions are open, and how
 * many credits they already hold.
 */
public record RegistrationContextResponse(
        UUID termId,
        String termName,
        LocalDate startDate,
        LocalDate endDate,
        Instant registrationOpensAt,
        Instant registrationClosesAt,
        Instant addDropOpensAt,
        Instant addDropClosesAt,
        LocalDate tuitionDueOn,
        String phase,
        boolean canAdd,
        boolean canDrop,
        boolean canWithdraw,
        UUID checkoutBatchId,
        Instant correctionExpiresAt,
        int correctionHours,
        boolean canUndoCheckout,
        int enrolledCredits,
        int minCredits,
        int maxCredits,
        BigDecimal amountPerCredit,
        BigDecimal campusFee,
        List<CatalogFeeQuote> catalogFees) {

    public static RegistrationContextResponse closed(CreditLoad load, TuitionQuote quote, int enrolledCredits) {
        return new RegistrationContextResponse(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "CLOSED",
                false,
                false,
                false,
                null,
                null,
                0,
                false,
                enrolledCredits,
                load.minSemesterCredits(),
                load.maxSemesterCredits(),
                quote.amountPerCredit(),
                quote.campusFee(),
                quote.catalogFees());
    }
}
