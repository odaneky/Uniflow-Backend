package com.university.lms.academic.dto;

import com.university.lms.academic.api.AcademicStructure.CreditLoad;
import com.university.lms.academic.domain.InstitutionAcademicPolicy;

public record AcademicPolicyResponse(
        int minSemesterCredits, int maxSemesterCredits, int checkoutCorrectionHours) {

    public static AcademicPolicyResponse from(InstitutionAcademicPolicy policy) {
        return new AcademicPolicyResponse(
                policy.getMinSemesterCredits(),
                policy.getMaxSemesterCredits(),
                policy.getCheckoutCorrectionHours());
    }

    public static AcademicPolicyResponse from(CreditLoad load) {
        return new AcademicPolicyResponse(load.minSemesterCredits(), load.maxSemesterCredits(), 48);
    }
}
