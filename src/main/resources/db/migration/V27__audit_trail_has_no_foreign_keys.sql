-- Removes the foreign key from the audit trail to the users table.
--
-- WHY: it deadlocked the identity-linking path, which is the first login of every student who was
-- provisioned in advance — the single most common new-user flow in the system.
--
-- The mechanism, because it is not obvious:
--
--   1. Linking updates users.keycloak_subject. That column carries a UNIQUE index, so PostgreSQL
--      promotes the row lock from FOR NO KEY UPDATE to a full FOR UPDATE.
--   2. The audit write runs in its own transaction (REQUIRES_NEW), so that a *refused* link is
--      still recorded after the caller's transaction rolls back.
--   3. Inserting an audit row whose actor_user_id referenced that user required a KEY SHARE lock
--      on it, to validate this foreign key.
--   4. KEY SHARE conflicts with FOR UPDATE. The child transaction blocked on the parent, and the
--      parent was waiting for the child to finish. Neither could proceed, and the request thread
--      hung indefinitely — under load, until the whole application stopped serving.
--
-- Beyond the deadlock, an audit trail should not hold foreign keys into the tables it audits at
-- all. It is an append-only record of identifiers, not a set of live references: coupling it to
-- another table's locks makes writing history contend with changing it, and a cascade or a
-- retention policy on the referenced row should never be able to rewrite what happened.
--
-- The column is kept — the id is still exactly what an investigator joins on, by hand, when they
-- want to know who did something. It simply stops being enforced, and stops taking locks.

ALTER TABLE audit_events DROP CONSTRAINT IF EXISTS fk_audit_events_actor;

-- Kept queryable: "everything this actor did" is the first question anyone asks of an audit trail.
CREATE INDEX IF NOT EXISTS idx_audit_events_actor ON audit_events (actor_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_action_time ON audit_events (action, occurred_at DESC);
