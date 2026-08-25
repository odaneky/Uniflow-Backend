package com.university.lms.finance.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

/**
 * The institution's withdrawal refund taper: three tiers, each a number of days past add/drop
 * close and the percentage refunded to a student who withdraws within it.
 *
 * <p>One settable row — see {@code institution_academic_policies} for the same shape. A refund
 * policy that changes going forward has no "what was it in the past" need the way tuition and
 * curriculum do: a withdrawal's refund is calculated once, at the moment it happens, and posted,
 * never recomputed later.
 */
@Entity
@Table(name = "refund_policies")
@Getter
public class RefundPolicy extends BaseEntity {

    public static final UUID SINGLETON_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-000000000003");

    @Column(name = "tier_1_days", nullable = false)
    private int tier1Days;

    @Column(name = "tier_1_pct", nullable = false, precision = 4, scale = 3)
    private BigDecimal tier1Pct;

    @Column(name = "tier_2_days", nullable = false)
    private int tier2Days;

    @Column(name = "tier_2_pct", nullable = false, precision = 4, scale = 3)
    private BigDecimal tier2Pct;

    @Column(name = "tier_3_days", nullable = false)
    private int tier3Days;

    @Column(name = "tier_3_pct", nullable = false, precision = 4, scale = 3)
    private BigDecimal tier3Pct;

    protected RefundPolicy() {}

    public RefundPolicy(
            int tier1Days,
            BigDecimal tier1Pct,
            int tier2Days,
            BigDecimal tier2Pct,
            int tier3Days,
            BigDecimal tier3Pct) {
        setId(SINGLETON_ID);
        replace(tier1Days, tier1Pct, tier2Days, tier2Pct, tier3Days, tier3Pct);
    }

    public void replace(
            int tier1Days,
            BigDecimal tier1Pct,
            int tier2Days,
            BigDecimal tier2Pct,
            int tier3Days,
            BigDecimal tier3Pct) {
        this.tier1Days = tier1Days;
        this.tier1Pct = tier1Pct;
        this.tier2Days = tier2Days;
        this.tier2Pct = tier2Pct;
        this.tier3Days = tier3Days;
        this.tier3Pct = tier3Pct;
    }

    /** The refund percentage for a withdrawal this many days after add/drop closed, or zero past every tier. */
    public BigDecimal percentFor(long daysSinceAddDropClosed) {
        if (daysSinceAddDropClosed < tier1Days) {
            return tier1Pct;
        }
        if (daysSinceAddDropClosed < tier2Days) {
            return tier2Pct;
        }
        if (daysSinceAddDropClosed < tier3Days) {
            return tier3Pct;
        }
        return BigDecimal.ZERO;
    }
}
