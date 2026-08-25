package com.university.lms.finance.dto;

import com.university.lms.finance.domain.RefundPolicy;
import java.math.BigDecimal;

public record RefundPolicyResponse(
        int tier1Days,
        BigDecimal tier1Pct,
        int tier2Days,
        BigDecimal tier2Pct,
        int tier3Days,
        BigDecimal tier3Pct) {

    public static RefundPolicyResponse from(RefundPolicy policy) {
        return new RefundPolicyResponse(
                policy.getTier1Days(),
                policy.getTier1Pct(),
                policy.getTier2Days(),
                policy.getTier2Pct(),
                policy.getTier3Days(),
                policy.getTier3Pct());
    }
}
