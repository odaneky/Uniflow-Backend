-- E4: the withdrawal refund taper (75/50/25/0% over weekly tiers past add/drop close) was hardcoded
-- constants in DefaultStudentBilling — nowhere for a registrar to set the institution's own dates
-- and percentages. One settable row, the same singleton shape institution_academic_policies
-- already uses: a refund policy that changes going forward has no "what was it in the past" need
-- the way tuition and curriculum do, since a withdrawal's refund is calculated once, at the moment
-- it happens, and posted — never recomputed later.
CREATE TABLE refund_policies (
    id           UUID         PRIMARY KEY,
    tier_1_days  INTEGER      NOT NULL,
    tier_1_pct   NUMERIC(4,3) NOT NULL,
    tier_2_days  INTEGER      NOT NULL,
    tier_2_pct   NUMERIC(4,3) NOT NULL,
    tier_3_days  INTEGER      NOT NULL,
    tier_3_pct   NUMERIC(4,3) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    CONSTRAINT ck_refund_policies_days
        CHECK (tier_1_days > 0 AND tier_2_days > tier_1_days AND tier_3_days > tier_2_days),
    CONSTRAINT ck_refund_policies_pct
        CHECK (tier_1_pct BETWEEN 0 AND 1 AND tier_2_pct BETWEEN 0 AND 1 AND tier_3_pct BETWEEN 0 AND 1
           AND tier_1_pct >= tier_2_pct AND tier_2_pct >= tier_3_pct)
);

INSERT INTO refund_policies (
    id, tier_1_days, tier_1_pct, tier_2_days, tier_2_pct, tier_3_days, tier_3_pct, created_at, updated_at
) VALUES (
    'aaaaaaaa-aaaa-4aaa-8aaa-000000000003', 7, 0.75, 14, 0.50, 21, 0.25, NOW(), NOW()
);
