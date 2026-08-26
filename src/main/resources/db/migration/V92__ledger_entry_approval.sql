-- E3: a manual ledger entry posted an arbitrary signed amount the instant one registrar submitted
-- it, changing what a student owes with no second party able to catch a mistake first. New manual
-- entries now start PENDING and are excluded from the balance until a different staff member
-- approves them. Automated postings (tuition charges, disbursements, self-service payments) default
-- to POSTED, so nothing about them changes.
ALTER TABLE account_entries
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    ADD COLUMN proposed_by UUID,
    ADD COLUMN decided_by UUID,
    ADD COLUMN decided_at TIMESTAMPTZ,
    ADD COLUMN decision_note VARCHAR(500);

ALTER TABLE account_entries ADD CONSTRAINT ck_account_entries_status
    CHECK (status IN ('PENDING', 'POSTED', 'REJECTED'));
