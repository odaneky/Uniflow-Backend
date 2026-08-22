# Keycloak

The identity provider for local development. Keycloak is on **http://localhost:8081**, admin
console `admin` / `admin`, realm **`university-lms`**.

## Login theme

The campus sign-in screens use the **`uniflow`** login theme
(`docker/keycloak/themes/uniflow`). It extends Keycloak’s built-in login theme so MFA, social
IdPs, password reset and locale switching keep working; only the markup and CSS are ours.

Brand tokens (colours, fonts, logo path) are at the top of
`themes/uniflow/login/resources/css/login.css`. Replace `resources/img/logo.svg` to change the mark.

**Enable it**

1. Recreate the Keycloak container so the theme volume is mounted:
   `docker compose up -d --force-recreate keycloak`
2. In the admin console: **university-lms → Realm settings → Themes → Login theme → uniflow → Save**.

`loginTheme` is also set in `realm/university-lms-realm.json`, but that file is imported only on
first start. An existing `keycloak-data` volume keeps the previous theme until you change it in
the console (or wipe that volume as described below).

The application validates tokens from this realm: `/api/v1/**` is `authenticated()`, and roles come
from `realm_access.roles`. See `common/security/` and [ROADMAP](../../docs/ROADMAP.md) P0.1 for
what remains (owner-scoped checks, retiring the local password store).

## What the realm contains

`realm/university-lms-realm.json` is imported on first start.

**Realm roles** — exactly the six names in `SecurityRoles.java`. They are kept identical on purpose:
a token's `realm_access.roles` becomes a Spring authority by prefixing `ROLE_`, so any divergence
between the two lists is an authorization hole that no compiler will catch.

`STUDENT` · `LECTURER` · `ACADEMIC_ADVISOR` · `FACULTY_ADMIN` · `REGISTRAR` · `SYSTEM_ADMIN`

**Clients**

| Client | Purpose |
|---|---|
| `university-lms-api` | The resource server. Every flow is disabled — it never initiates a login, it only validates bearer tokens. It exists so tokens can carry it as an `aud`. |
| `university-lms-dev` | **Dev only.** Public client with direct access grants, so a token is one `curl` away. Carries an audience mapper that puts `university-lms-api` in `aud`. |
| `university-lms-web` | The React portal. Authorization-code + PKCE. Realm roles are mapped onto `realm_access.roles` in the access token so UniFlow can pick admin vs student chrome. |

A public client with the password grant is a deliberate local-development convenience and must not
reach any deployed environment. Real clients use the authorization-code flow with PKCE; the
redirect URIs for the React app (`:5173`) are already registered for when that lands.

**Users** — all with password `password`:

`admin.lms` (SYSTEM_ADMIN) · `registrar` (REGISTRAR) · `lecturer` (LECTURER) ·
`advisor` (ACADEMIC_ADVISOR) · `student` (STUDENT)

The UniFlow portal admin is **`admin.lms`**, not the Keycloak console user `admin` / `admin`. The
console user lives in the `master` realm and has no campus role, so signing into UniFlow as `admin`
lands you in the student shell (or fails to match a campus user).

If an existing Keycloak volume was imported before `admin.lms` had `SYSTEM_ADMIN`, assign that
realm role in **Users → admin.lms → Role mapping**, or wipe `keycloak-data` so the JSON re-imports
(see below). After adding the realm-roles mapper to `university-lms-web`, sign out once so a new
access token is issued.

## Get a token

```bash
curl -s -X POST http://localhost:8081/realms/university-lms/protocol/openid-connect/token -d grant_type=password -d client_id=university-lms-dev -d username=registrar -d password=password
```

Then use it:

```bash
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/courses
```

The values the application is configured with (`application-local.yml`), overridable by
`KEYCLOAK_ISSUER_URI` / `KEYCLOAK_JWK_SET_URI`:

```
issuer-uri  http://localhost:8081/realms/university-lms
jwk-set-uri http://localhost:8081/realms/university-lms/protocol/openid-connect/certs
audience    university-lms-api
```

The audience is checked as well as the issuer. A token minted for a different client in this realm
is rejected — including one from the `master` realm's `admin-cli`, which is otherwise perfectly
valid.

## Which role can do what

Enforced in `SecurityConfig`; asserted by `AuthorizationRulesIntegrationTest` and folder 07 of the
Postman collection.

| Action | Roles |
|---|---|
| Read the catalog, structure, calendar | any authenticated user |
| Create users, grant roles, suspend accounts | `SYSTEM_ADMIN` |
| Create faculty / department / programme | `SYSTEM_ADMIN`, `FACULTY_ADMIN` |
| Create academic years and terms, open registration | `SYSTEM_ADMIN`, `REGISTRAR` |
| Create and amend courses and sections | `SYSTEM_ADMIN`, `REGISTRAR`, `FACULTY_ADMIN` |
| Create and amend student records | `SYSTEM_ADMIN`, `REGISTRAR` |
| Enrol, drop, withdraw | `SYSTEM_ADMIN`, `REGISTRAR`, `ACADEMIC_ADVISOR`, `STUDENT` |
| Complete an enrolment | `SYSTEM_ADMIN`, `REGISTRAR`, `LECTURER` |

Roles are only the first layer. **Ownership** is checked in the service layer: a student may read
and act on their own record and enrolments, and gets `403 ACCESS_DENIED` for anyone else's — for
writes as well as reads. See `docs/ROADMAP.md`, *Owner-scoping*.

## From a token to a person

A Keycloak token carries a `sub` and some roles, and nothing that names a row in the application's
database. `users.keycloak_subject` is the join. The first request a subject ever makes provisions
that row automatically, so a Keycloak account becomes usable without anyone creating it by hand:

```bash
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/me
```

The seeded realm users start with **no student record** — they are accounts, not students. To make
`student` an actual student, resolve them and then create the record as an administrator; folder 03
of the Postman collection does exactly this.

Linking an existing local account is only done on a **verified** email. An unverified address is a
claim the caller made about themselves, and honouring it would let anyone able to register in the
realm with someone else's address inherit that person's records.

## Re-importing after you change the realm file

Import happens **once**, into Keycloak's database. Editing the JSON afterwards has no effect until
that state is discarded:

```bash
docker compose down && docker volume rm unipro-backend_keycloak-data
```

```bash
docker compose exec -T postgres psql -U lms -d postgres -c 'DROP DATABASE keycloak;'
```

Then `docker compose up -d` re-imports. This touches only Keycloak's own state — the application's
`university_lms` database and its Flyway history are in a different database and are unaffected.

To go the other way and capture changes made in the admin console:

```bash
docker compose exec keycloak /opt/keycloak/bin/kc.sh export --dir /tmp/export --realm university-lms --users realm_file
```

```bash
docker compose cp keycloak:/tmp/export/university-lms-realm.json docker/keycloak/realm/
```

## Where Keycloak's data lives

Its own `keycloak` database inside the same PostgreSQL container, created by the one-shot
`keycloak-db-init` service. Sharing the instance saves a container; sharing a *database* would not
be safe, because Keycloak migrates its own schema and Hibernate's `ddl-auto: validate` would then
see tables Flyway never created.

`keycloak-db-init` exists rather than a `/docker-entrypoint-initdb.d` script because those run only
when the data directory is empty. Any machine that ran this stack before Keycloak was added already
has a populated volume, so the script would silently never fire.

## Not production

`start-dev` means no TLS, dev-mode caching, and `KC_HOSTNAME_STRICT=false`. A deployed instance
needs `start`, a fixed hostname, TLS, real admin credentials from a secret store, and
`sslRequired: "all"` on the realm.
