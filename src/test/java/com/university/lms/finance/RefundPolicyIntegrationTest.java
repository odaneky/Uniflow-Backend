package com.university.lms.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.university.lms.common.exception.ValidationException;
import com.university.lms.finance.dto.ReplaceRefundPolicyRequest;
import com.university.lms.finance.dto.RefundPolicyResponse;
import com.university.lms.finance.service.RefundPolicyService;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.RunAs;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * E4: the withdrawal refund taper is now a settable institution policy — proves the real proxied
 * bean can read the seeded default, replace it, and refuses a request whose tiers are not strictly
 * ordered rather than letting {@code ck_refund_policies_days}/{@code ck_refund_policies_pct} answer
 * with a bare {@code DATA_INTEGRITY_VIOLATION}.
 */
class RefundPolicyIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private RefundPolicyService refundPolicyService;

    @Test
    @DisplayName("the seeded default is readable and replacing it persists the new tiers")
    void replacingThePolicyPersists() throws Exception {
        RefundPolicyResponse before = refundPolicyService.find();
        assertThat(before.tier1Days()).isEqualTo(7);

        RefundPolicyResponse after = RunAs.staff(() -> refundPolicyService.replace(new ReplaceRefundPolicyRequest(
                10, new BigDecimal("0.80"), 20, new BigDecimal("0.40"), 30, new BigDecimal("0.10"))));

        assertThat(after.tier1Days()).isEqualTo(10);
        assertThat(after.tier1Pct()).isEqualByComparingTo("0.80");
        assertThat(refundPolicyService.find().tier3Pct()).isEqualByComparingTo("0.10");
    }

    @Test
    @DisplayName("out-of-order day thresholds are refused with a specific reason, not a constraint violation")
    void outOfOrderDaysRefused() {
        assertThatThrownBy(() -> RunAs.staff(() -> refundPolicyService.replace(new ReplaceRefundPolicyRequest(
                        14, new BigDecimal("0.75"), 7, new BigDecimal("0.50"), 21, new BigDecimal("0.25")))))
                .isInstanceOf(ValidationException.class);
    }
}
