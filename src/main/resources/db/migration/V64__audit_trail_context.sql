-- The audit trail recorded who did what to which entity, but not what changed, why, or from
-- where. "What was this grade before it was changed?" — the first question any appeal or
-- accreditation review asks — was unanswerable from this table alone.
--
-- before_value/after_value are JSONB rather than a second free-text column: a structured diff is
-- what a reviewer or a UI can actually render, and letting callers keep writing prose into
-- `details` for the parts that are genuinely unstructured is still available — this is additive.
--
-- source_ip and correlation_id are populated by DefaultAuditTrail itself from the current request,
-- never by the caller, so every write through the existing record() overloads gets them for free
-- without every call site needing to change. reason and the before/after snapshot are populated
-- only by callers that pass them — most of today's ~90 call sites do not yet, and that is expected;
-- retrofitting them is separate, follow-on work, not a schema concern.
--
-- No foreign keys: V27 established that the trail must not take locks on, or be rewritten by, the
-- tables it audits, and that reasoning applies to every column here exactly as it did to
-- actor_user_id.

ALTER TABLE audit_events
    ADD COLUMN reason         VARCHAR(1000),
    ADD COLUMN before_value   JSONB,
    ADD COLUMN after_value    JSONB,
    ADD COLUMN source_ip      VARCHAR(45),
    ADD COLUMN correlation_id VARCHAR(64);

CREATE INDEX idx_audit_events_correlation ON audit_events (correlation_id) WHERE correlation_id IS NOT NULL;
