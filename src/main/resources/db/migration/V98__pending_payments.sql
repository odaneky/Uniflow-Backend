-- E7: the pending/unsettled state the ledger lacks — an online payment between "the student
-- clicked pay" and "the gateway confirmed it", which today has nowhere to live at all.
CREATE TABLE pending_payments (
    id                  UUID PRIMARY KEY,
    account_id          UUID NOT NULL REFERENCES student_accounts (id),
    amount              NUMERIC(12,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider            VARCHAR(20) NOT NULL,
    provider_reference  VARCHAR(255) NOT NULL,
    account_entry_id    UUID REFERENCES account_entries (id),
    failure_reason      VARCHAR(500),
    settled_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    CONSTRAINT uk_pending_payments_provider_ref UNIQUE (provider, provider_reference),
    CONSTRAINT ck_pending_payments_status CHECK (status IN ('PENDING', 'SETTLED', 'FAILED'))
);

CREATE INDEX idx_pending_payments_account ON pending_payments (account_id);
