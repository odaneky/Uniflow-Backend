package com.university.lms.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.finance.domain.FeeApplicability;
import com.university.lms.finance.domain.FeeAssessment;
import com.university.lms.finance.domain.FeeCatalogItem;
import com.university.lms.finance.domain.FeeKind;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FeeApplicabilityTest {

    private static final UUID PROGRAMME = UUID.randomUUID();
    private static final UUID COURSE = UUID.randomUUID();

    @Test
    void universityWideFeeAppliesToAnyEnrolment() {
        FeeCatalogItem fee = fee(null, null, FeeAssessment.ONCE_PER_TERM);
        assertThat(FeeApplicability.applies(fee, PROGRAMME, COURSE)).isTrue();
        assertThat(FeeApplicability.matchesProgramme(fee, PROGRAMME)).isTrue();
    }

    @Test
    void programmeFeeDoesNotApplyToAnotherProgramme() {
        FeeCatalogItem fee = fee(null, PROGRAMME, FeeAssessment.ONCE_PER_TERM);
        assertThat(FeeApplicability.applies(fee, UUID.randomUUID(), COURSE)).isFalse();
        assertThat(FeeApplicability.matchesProgramme(fee, PROGRAMME)).isTrue();
    }

    @Test
    void courseFeeAppliesOnlyToThatCourse() {
        FeeCatalogItem fee = fee(COURSE, null, FeeAssessment.PER_ENROLMENT);
        assertThat(FeeApplicability.applies(fee, PROGRAMME, COURSE)).isTrue();
        assertThat(FeeApplicability.applies(fee, PROGRAMME, UUID.randomUUID())).isFalse();
        assertThat(FeeApplicability.matchesProgramme(fee, PROGRAMME)).isTrue();
    }

    @Test
    void inactiveFeeNeverApplies() {
        FeeCatalogItem fee = fee(null, null, FeeAssessment.ONCE_PER_TERM);
        fee.deactivate();
        assertThat(FeeApplicability.applies(fee, PROGRAMME, COURSE)).isFalse();
        assertThat(FeeApplicability.matchesProgramme(fee, PROGRAMME)).isFalse();
    }

    @Test
    void perCreditAmountScalesWithLoad() {
        FeeCatalogItem fee = fee(null, null, FeeAssessment.PER_CREDIT);
        assertThat(FeeApplicability.amount(fee, 3)).isEqualByComparingTo("30.00");
        assertThat(FeeApplicability.amount(fee(null, null, FeeAssessment.PER_ENROLMENT), 3))
                .isEqualByComparingTo("10.00");
    }

    private static FeeCatalogItem fee(UUID courseId, UUID programmeId, FeeAssessment assessment) {
        return new FeeCatalogItem(
                "Test fee",
                "—",
                new BigDecimal("10.00"),
                FeeKind.MISCELLANEOUS,
                assessment,
                courseId,
                programmeId);
    }
}
