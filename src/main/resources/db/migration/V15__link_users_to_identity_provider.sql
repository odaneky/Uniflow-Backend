-- Links a local user row to the identity provider's subject.
--
-- WHY: Keycloak authenticates, but its tokens carry no application identity — no student number,
-- no user id, nothing that resolves to a row here. Without this column an authenticated caller is
-- anonymous to the domain: the application knows *a* student is calling but not *which*, so no
-- endpoint can answer "my courses" and no check can ask "is this yours". This is the join.
--
-- The subject is Keycloak's `sub` claim: opaque, stable for the lifetime of the account, and not
-- the username — usernames and email addresses change, and re-pointing every record when someone
-- marries is exactly the failure this avoids.

ALTER TABLE users ADD COLUMN keycloak_subject VARCHAR(255);

-- Unique, but nullable: PostgreSQL permits many NULLs in a unique index, so rows predating the
-- identity provider stay valid while no two users can ever claim the same subject. That guarantee
-- has to be the database's — an application check would be a lost update under concurrent
-- just-in-time provisioning of the same person.
CREATE UNIQUE INDEX uk_users_keycloak_subject ON users (keycloak_subject);

-- Provisioned users have no local password, and cannot: Keycloak holds the credential. The column
-- survives this migration only because dropping it is a separate decision with its own blast
-- radius (see docs/ROADMAP.md, P0.1); relaxing NOT NULL is what lets it stop being written at all.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
