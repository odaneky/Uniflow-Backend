package com.university.lms.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.university.lms.academic.api.AcademicStructure.CreditLoad;
import com.university.lms.academic.domain.AcademicErrorCode;
import com.university.lms.academic.domain.CreditLoadPolicy;
import com.university.lms.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

class CreditLoadPolicyTest {

    @Test
    void institutionDefaultsWhenTheProgrammeHasNoOverride() {
        CreditLoad load = CreditLoadPolicy.resolve(12, 18, null, null);
        assertThat(load.minSemesterCredits()).isEqualTo(12);
        assertThat(load.maxSemesterCredits()).isEqualTo(18);
        assertThat(load.programmeOverride()).isFalse();
    }

    @Test
    void programmeMayRaiseTheMaximum() {
        CreditLoad load = CreditLoadPolicy.resolve(12, 18, 15, 21);
        assertThat(load.minSemesterCredits()).isEqualTo(15);
        assertThat(load.maxSemesterCredits()).isEqualTo(21);
        assertThat(load.programmeOverride()).isTrue();
    }

    @Test
    void invertedRangeIsRefused() {
        assertThatThrownBy(() -> CreditLoadPolicy.resolve(18, 12, null, null))
                .isInstanceOf(ValidationException.class)
                .satisfies(thrown -> assertThat(((ValidationException) thrown).getErrorCode())
                        .isEqualTo(AcademicErrorCode.INVALID_CREDIT_LOAD));
    }
}
