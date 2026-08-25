package com.university.lms.finance.service;

import com.university.lms.administration.api.Auditable;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.ValidationException;
import com.university.lms.finance.domain.FinanceErrorCode;
import com.university.lms.finance.domain.RefundPolicy;
import com.university.lms.finance.dto.ReplaceRefundPolicyRequest;
import com.university.lms.finance.dto.RefundPolicyResponse;
import com.university.lms.finance.repository.RefundPolicyRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * E4: the withdrawal refund taper used to be hardcoded constants in {@code DefaultStudentBilling}
 * — nowhere for a registrar to set the institution's own dates and percentages. This is that
 * setting, read by {@code DefaultStudentBilling.withdrawalRefundPercent} at the moment a withdrawal
 * is processed.
 */
@Service
@Transactional(readOnly = true)
public class RefundPolicyService {

    private final RefundPolicyRepository repository;

    public RefundPolicyService(RefundPolicyRepository repository) {
        this.repository = repository;
    }

    public RefundPolicyResponse find() {
        return RefundPolicyResponse.from(requirePolicy());
    }

    RefundPolicy current() {
        return requirePolicy();
    }

    @Auditable(
            action = AuditTrail.Action.REFUND_POLICY_REPLACED,
            entityType = AuditTrail.EntityType.REFUND_POLICY,
            entityId = "null",
            details = "'Refund policy replaced'")
    @Transactional
    public RefundPolicyResponse replace(ReplaceRefundPolicyRequest request) {
        requireOrdered(request);
        RefundPolicy policy = requirePolicy();
        policy.replace(
                request.tier1Days(),
                request.tier1Pct(),
                request.tier2Days(),
                request.tier2Pct(),
                request.tier3Days(),
                request.tier3Pct());
        return RefundPolicyResponse.from(policy);
    }

    /**
     * Enforced here rather than left to {@code ck_refund_policies_days}/{@code
     * ck_refund_policies_pct}: those exist as the constraint of last resort, but a caller deserves
     * a specific field-level reason, not a generic {@code DATA_INTEGRITY_VIOLATION}.
     */
    private void requireOrdered(ReplaceRefundPolicyRequest request) {
        if (!(request.tier1Days() < request.tier2Days() && request.tier2Days() < request.tier3Days())) {
            throw new ValidationException(
                    FinanceErrorCode.INVALID_REFUND_POLICY, "Tier day thresholds must strictly increase");
        }
        if (!nonIncreasing(request.tier1Pct(), request.tier2Pct(), request.tier3Pct())) {
            throw new ValidationException(
                    FinanceErrorCode.INVALID_REFUND_POLICY,
                    "Tier percentages must not increase from an earlier tier to a later one");
        }
    }

    private boolean nonIncreasing(BigDecimal a, BigDecimal b, BigDecimal c) {
        return a.compareTo(b) >= 0 && b.compareTo(c) >= 0;
    }

    private RefundPolicy requirePolicy() {
        return repository
                .findById(RefundPolicy.SINGLETON_ID)
                .orElseGet(() -> repository.save(new RefundPolicy(
                        7, new BigDecimal("0.75"), 14, new BigDecimal("0.50"), 21, new BigDecimal("0.25"))));
    }
}
