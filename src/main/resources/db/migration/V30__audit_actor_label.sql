-- Snapshot the actor's display name onto the trail.
--
-- The trail is read by the registry, who cannot list users (that is SYSTEM_ADMIN only). Resolving
-- names at read time would also require administration to depend on identity — and identity already
-- depends on administration.api.AuditTrail, which would be a cycle.
--
-- A label written at the time of the event is what an investigator sees even after the account is
-- renamed or removed. actor_user_id remains the identifier they join on by hand.

ALTER TABLE audit_events ADD COLUMN actor_label VARCHAR(200);
