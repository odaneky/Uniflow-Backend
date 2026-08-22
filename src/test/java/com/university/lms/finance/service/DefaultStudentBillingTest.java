package com.university.lms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultStudentBillingTest {

    private static final int REFERENCE_COLUMN = 120;
    private static final UUID FEE_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-000000000011");
    private static final UUID TERM_ID = UUID.fromString("40935863-cdd0-4508-b27b-b897a6538888");
    private static final UUID ENROLMENT_ID = UUID.fromString("ba0e15e1-708f-49dc-b0a4-0e1b3e637ded");

    @Test
    @DisplayName("idempotent billing keys fit account_entries.reference")
    void catalogBillingKeysFitTheReferenceColumn() {
        assertThat(DefaultStudentBilling.catalogTermReference(FEE_ID, TERM_ID).length())
                .isLessThanOrEqualTo(REFERENCE_COLUMN);
        assertThat(DefaultStudentBilling.catalogTermCreditReference(FEE_ID, TERM_ID).length())
                .isLessThanOrEqualTo(REFERENCE_COLUMN);
        assertThat(DefaultStudentBilling.catalogEnrolmentReference(FEE_ID, ENROLMENT_ID).length())
                .isLessThanOrEqualTo(REFERENCE_COLUMN);
        assertThat(DefaultStudentBilling.catalogEnrolmentCreditReference(FEE_ID, ENROLMENT_ID).length())
                .isLessThanOrEqualTo(REFERENCE_COLUMN);
    }
}
