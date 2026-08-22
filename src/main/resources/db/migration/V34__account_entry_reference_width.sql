-- Idempotent billing keys are "fee:{uuid}:term:{uuid}" (82) and "fee-credit:{uuid}:enrol:{uuid}" (90).
-- VARCHAR(80) truncated the Registration Fee charge and aborted checkout.

ALTER TABLE account_entries
    ALTER COLUMN reference TYPE VARCHAR(120);
