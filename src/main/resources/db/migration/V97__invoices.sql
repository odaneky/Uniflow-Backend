-- E6: a billable document distinct from the running ledger. AccountEntry rows post continuously as
-- charges and credits happen; an invoice is a snapshot of a term's charges at a point in time —
-- what gets printed, emailed or sent to a third-party sponsor — that does not silently change if a
-- later correction touches the underlying entries.
CREATE TABLE invoices (
    id                UUID PRIMARY KEY,
    student_id        UUID NOT NULL,
    academic_term_id  UUID NOT NULL,
    invoice_number    VARCHAR(20) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    total_amount      NUMERIC(12,2) NOT NULL,
    currency          VARCHAR(3) NOT NULL,
    issued_at         TIMESTAMPTZ NOT NULL,
    due_on            DATE NOT NULL,
    -- Null bill-to means the student is billed directly; set means a third party (an employer, a
    -- government sponsor, a scholarship trust) is — E6's sponsor-billing half. Deliberately a name
    -- and an email rather than a modeled Sponsor relationship, which is separate, larger work.
    bill_to_name      VARCHAR(200),
    bill_to_email     VARCHAR(255),
    notes             VARCHAR(1000),
    paid_at           TIMESTAMPTZ,
    voided_at         TIMESTAMPTZ,
    void_reason       VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    CONSTRAINT uk_invoices_number UNIQUE (invoice_number),
    CONSTRAINT ck_invoices_status CHECK (status IN ('ISSUED', 'PAID', 'VOID'))
);

CREATE INDEX idx_invoices_student ON invoices (student_id, academic_term_id);
CREATE INDEX idx_invoices_due_on ON invoices (due_on) WHERE status = 'ISSUED';

-- Snapshotted line items — copied from AccountEntry at issue time, not a live reference, for the
-- same reason the invoice's own total is frozen rather than recomputed.
CREATE TABLE invoice_line_items (
    id          UUID PRIMARY KEY,
    invoice_id  UUID NOT NULL REFERENCES invoices (id),
    description VARCHAR(500) NOT NULL,
    amount      NUMERIC(12,2) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100)
);

CREATE INDEX idx_invoice_line_items_invoice ON invoice_line_items (invoice_id);
