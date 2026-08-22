package com.university.lms.finance.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Whether a catalog fee applies to this enrolment, and how much to post. */
public final class FeeApplicability {

    private FeeApplicability() {}

    public static boolean applies(FeeCatalogItem fee, UUID programmeId, UUID courseId) {
        if (fee == null || !fee.isActive()) {
            return false;
        }
        if (fee.getProgrammeId() != null && !fee.getProgrammeId().equals(programmeId)) {
            return false;
        }
        if (fee.getCourseId() != null && !fee.getCourseId().equals(courseId)) {
            return false;
        }
        return true;
    }

    /** Programme-matching fees, including course-scoped ones the UI will filter. */
    public static boolean matchesProgramme(FeeCatalogItem fee, UUID programmeId) {
        if (fee == null || !fee.isActive()) {
            return false;
        }
        return fee.getProgrammeId() == null || fee.getProgrammeId().equals(programmeId);
    }

    public static BigDecimal amount(FeeCatalogItem fee, int credits) {
        if (fee.getAssessment() == FeeAssessment.PER_CREDIT) {
            return fee.getAmount().multiply(BigDecimal.valueOf(Math.max(credits, 0)));
        }
        return fee.getAmount();
    }
}
