-- Removes UniFlow's second identity system.
--
-- Two stores are dropped here. Both were inert, and that is precisely why they were dangerous: each
-- looked like a working security control while having no effect on anything.
--
-- AUDIT PERFORMED BEFORE THIS MIGRATION (see docs/identity-architecture-assessment.md):
--   * no production deployment exists;
--   * every row holding a password_hash also had a NULL keycloak_subject, i.e. was created by the
--     old local endpoint and could never authenticate in the first place;
--   * user_roles held a handful of development grants that no authorization path ever read.
-- Nothing here destroys a credential anyone was relying on.

-- 1. The local credential store.
--
-- Keycloak owns authentication. This column has not been written since just-in-time provisioning
-- landed and has never been read by an authentication path — the application has no login endpoint.
-- Leaving it would keep a field that looks authoritative, invites a future "password reset"
-- feature, and would eventually be populated by accident.
ALTER TABLE users DROP COLUMN password_hash;

-- 2. The local role-grant store.
--
-- Authorities come from the token's realm roles. A row in user_roles therefore granted nothing, and
-- deleting one revoked nothing, while the endpoint that wrote it reported success either way. Roles
-- are now read from and written to the identity provider, which is the only place they mean
-- anything.
--
-- `roles`, `permissions` and `role_permissions` are deliberately KEPT. They are not a duplicate
-- identity store: they are UniFlow's own permission catalogue, for the resource-level authorization
-- that roles alone cannot express (a lecturer may grade *their* sections, not every section). The
-- seeded role names intentionally mirror the realm's so the two vocabularies stay aligned.
DROP TABLE IF EXISTS user_roles;
