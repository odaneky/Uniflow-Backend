# Identity architecture — assessment and migration plan

An inspection of the existing backend against the UniFlow identity architecture: Keycloak owns
identity, external university systems originate institutional student identity, UniFlow owns
academic and business data.

**STATUS: implemented.** This began as the inspection step; the migration plan below has since been
carried out. What each phase actually produced is recorded under *Outcome* at the end.

**Headline:** the foundations are already right — the application is a real OAuth2 resource server
with no login of its own, ownership checks are enforced and tested, and Keycloak configuration is
externalised. Three things conflict, and two of those are *illusions of control* that are more
dangerous than plain gaps because they look like working features.

---

## 1. Current authentication architecture

The application is an OAuth2 **resource server** and nothing more.

```
Keycloak (:8081, realm university-lms)
   │  OIDC / JWT
   ▼
BearerTokenAuthenticationFilter
   │  signature (JWKS) + issuer + audience
   ▼
KeycloakRealmRoleConverter   realm_access.roles → ROLE_*
   ▼
SecurityConfig               coarse role rules, one ordered list
   ▼
Service layer                ownership checks
```

- No login endpoint, no session, no CSRF flow, no locally issued JWT. `SessionCreationPolicy.STATELESS`.
- `SecurityConfig.jwtDecoder` validates **signature, issuer and audience**. Audience matters: every
  client in the realm shares an issuer, so issuer-only acceptance would make any future Keycloak
  client a way in. Verified — a `master`-realm `admin-cli` token is rejected.
- Decoder is built from the JWK set URI rather than issuer discovery, so Keycloak is not a hard
  start-up dependency.
- `SecurityErrorResponder` renders `401`/`403` in the standard error envelope and discloses no
  decode reason.

## 2. Current User model

`identity.domain.User` → `users`

| Column | Note |
|---|---|
| `id` | internal UUID |
| `username`, `email` | unique |
| `first_name`, `last_name` | |
| `status` | `PENDING_ACTIVATION` / `ACTIVE` / `SUSPENDED` / `DEACTIVATED` |
| `keycloak_subject` | **unique, nullable** — the link to the identity provider |
| `password_hash` | **nullable — conflicts with the target architecture** |

`users` is referenced by fourteen tables (`students`, `course_sections.lecturer_user_id`,
`documents`, `notifications`, …). It is the universal "person" anchor in the schema, which is why it
should stay — the local row is a *projection* of an identity, not a second identity.

`User.fromIdentityProvider(...)` creates a row with no password, status `ACTIVE`.
`User.changePasswordHash(...)` still exists and is unused by any endpoint.

## 3. Current Student model

`student.domain.Student` → `students`

| Column | Note |
|---|---|
| `id` | internal UUID |
| `user_id` | **unique** FK to `users` |
| `student_number` | **unique**, `^[A-Za-z0-9/-]+$`, max 30 — supplied by the caller, never generated here |
| `programme_id`, `status`, `admission_date`, `expected_graduation_date`, `profile_id`, `version` | academic data |

Student carries **no** `external_identity_id` of its own. The link to Keycloak is reached through
`Student.user_id → User.keycloak_subject`.

## 4. Current security configuration

Two layers, deliberately separated:

- **Role rules** — `SecurityConfig.filterChain`, one ordered list, first match wins. Covers user
  administration (`SYSTEM_ADMIN`), academic structure, calendar, catalog, student records and
  enrolment.
- **Ownership** — service layer, via `identity.api.CurrentUserProvider` and
  `CurrentUser.requireSelfOrStaff`. A student may read and act on their own record and enrolments
  only; `GET /students` is refused outright for students.

`@EnableMethodSecurity` is on but no `@PreAuthorize` is used yet.

## 5. Current Keycloak configuration

`docker/keycloak/realm/university-lms-realm.json`, imported on first start.

- Realm roles: `STUDENT`, `LECTURER`, `ACADEMIC_ADVISOR`, `FACULTY_ADMIN`, `REGISTRAR`,
  `SYSTEM_ADMIN` — identical to `SecurityRoles`, on purpose.
- Clients: `university-lms-api` (all flows off — audience only) and `university-lms-dev` (public,
  direct access grants, **development only**).
- Users: `admin.lms`, `registrar`, `lecturer`, `advisor`, `student` — all password `password`.
- Config externalised: `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWK_SET_URI`, `JWT_AUDIENCE`. Defaults in
  `application-local.yml`; `application-prod.yml` has **no** defaults and fails to start without
  them. No secrets committed.

## 6. Identity-related database tables

| Table | Role |
|---|---|
| `users` | local projection of an identity; `keycloak_subject` unique |
| `students` | academic record; `user_id` unique, `student_number` unique |
| `roles`, `permissions`, `role_permissions`, `user_roles` | a **local authorization store that nothing reads** |
| `audit_events` | exists, **never written** |

## 7. What already matches this architecture

| Requirement | Status |
|---|---|
| §30 OAuth2 resource server, issuer/JWK validation | ✅ plus audience validation |
| §28 exactly one authentication authority | ✅ no login, no session, no locally issued token |
| §12 never trust identity from path/body/header | ✅ enforced and tested |
| §38 the `students/{otherId}` scenario | ✅ `403 ACCESS_DENIED`, for writes as well as reads |
| §24 resource-level authorization | ✅ for student self-service |
| §14 security abstraction | ✅ `CurrentUserProvider` / `CurrentUser`; only 3 classes touch `Jwt` directly |
| §7 UniFlow does not generate the student number | ✅ supplied by the caller |
| §7/§39 stable identity mapping, unique | ✅ `uk_users_keycloak_subject`, `uk_students_user`, `uk_students_student_number` |
| §29 externalised config, no hard-coded secrets | ✅ |
| §26 deactivation over deletion | ✅ no `DELETE` endpoints anywhere |
| §37 identity failures use the global error contract | ✅ |
| §16 `/me` API | ⚠️ exists (`/api/v1/me`, `/api/v1/students/me`) but thin |

## 8. What conflicts with this architecture

### C1 — A second credential store still exists (§3, §28, §43)

`users.password_hash`, `PasswordEncoder`, `CreateUserRequest.password`, `User.changePasswordHash()`.
`POST /api/v1/users` still **requires** a password and BCrypts it.

Nothing authenticates against it and just-in-time provisioning never writes it, so this is not an
active vulnerability — it is a dormant credential store that looks authoritative. It also produces
accounts nobody can log in as, because Keycloak has never heard of them.

### C2 — Local account status is an illusion (§25, §26, §35)

`POST /api/v1/users/{id}/suspend` sets `users.status = SUSPENDED`. **Nothing consults that status at
request time.** `User.canAuthenticate()` is called in exactly one place — to populate a boolean on a
read DTO.

An administrator who suspends a user will believe they have cut off access. The user's Keycloak
account is untouched, their token still validates, and every endpoint still serves them. This is the
most dangerous item in the report: a security control that reports success and does nothing.

### C3 — Local roles are a second, inert authorization store (§19, §23, §28)

`POST /api/v1/users/{id}/roles` writes `user_roles`. Authorities come from the token's
`realm_access.roles`. Granting a role in UniFlow therefore **changes nothing**, and revoking one
grants no protection. Same failure shape as C2.

### C4 — Login identifier is not the student ID (§4)

The realm's usernames are `student`, `registrar`, `lecturer`. The architecture requires students to
authenticate with their institutional student ID (`202012345`), which should become the Keycloak
`username` and therefore `preferred_username`.

### C5 — Nothing correlates a login to an existing Student record (§17, §39)

This is the functional consequence of C4 and the biggest gap.

When a student logs in today, `UserProvisioningService` looks for an existing local user **by email
only**. If no match, it creates a brand-new `users` row — with no connection to the `students` row
the registrar already created. The student then authenticates successfully and has no academic
record, because correlation happened on an attribute that is not the institutional identifier.

The architecture names the correct correlation keys: `studentNumber` and/or `externalIdentityId`.
Email is neither, and email-matching is also the riskiest of the three (mitigated today by requiring
`email_verified`, but it should not be the primary key of correlation).

### C6 — No provisioning or Keycloak integration boundary (§20, §21)

`identity/` has `domain`, `dto`, `repository`, `service`, `web`, `api` — no `integration/`. There is
no `IdentityProvisioningService`, no `StudentProvisioningService`, no `KeycloakIdentityClient`, and
just-in-time provisioning on first token is hard-coded as the only mechanism. §5 requires an
abstraction so batch, event or API provisioning can be added without rework.

### C7 — Claim names are hard-coded (§13)

`preferred_username`, `given_name`, `family_name`, `email_verified` in `UserProvisioningService`;
`realm_access` in `KeycloakRealmRoleConverter`. A federated IdP (§33) or a realm with a different
mapper would need code changes.

### C8 — `/me` API is thin (§16)

Only `GET /api/v1/me` and `GET /api/v1/students/me`. Missing `/me/profile`, `/me/courses`,
`/me/registration`, `/me/grades`, `/me/notifications`.

### C9 — Identity events are not audited (§36)

`audit_events` exists and is never written. No record of provisioning, identity linking, role
change, deactivation or authorization failure.

### C10 — Frontend has no authentication at all (§31)

The React prototype has no Keycloak integration, no token handling, no `Authorization` header. Out
of scope for the backend but it blocks any end-to-end demonstration.

---

## A deliberate deviation worth keeping

The architecture sketches `Student.externalIdentityId` (§7, §39). The implementation instead puts
`keycloak_subject` on `User` and reaches it as `Student.user_id → User.keycloak_subject`.

**Recommendation: keep it.** Lecturers, deans, department heads and advisors are all identities that
are not students; `users` is already the anchor for fourteen foreign keys. Putting the subject on
`Student` would either duplicate it per person-type or leave non-student identities unlinkable.

The uniqueness §39 demands holds transitively — `uk_users_keycloak_subject` plus `uk_students_user`
makes two Student records sharing one Keycloak identity impossible — but that invariant is currently
implicit. It should be stated in `docs/database.md` and pinned by a test.

---

## 9. What needs to change

| # | Change | Addresses | Risk |
|---|---|---|---|
| 1 | Make suspension real, or remove it | C2 | **High** — silent security failure today |
| 2 | Make local role grants real, or remove them | C3 | **High** — same shape |
| 3 | Correlate login to Student by student number | C5 | High — students otherwise land with no academic record |
| 4 | Realm usernames become student IDs | C4 | Medium |
| 5 | Remove the local credential store | C1 | Low — dormant, but data migration must be checked first |
| 6 | Provisioning abstraction + Keycloak integration boundary | C6 | Medium |
| 7 | Configurable claim mapping | C7 | Low |
| 8 | Expand `/me` | C8 | Low |
| 9 | Audit identity events | C9 | Low |
| 10 | Frontend OIDC | C10 | Separate workstream |

For items 1 and 2 the honest choice is usually **remove**, not implement. UniFlow suspending a
Keycloak account means calling Keycloak's admin API — which is exactly the integration boundary of
C6, so these are the same piece of work. Until that exists, an endpoint that pretends to suspend is
worse than no endpoint.

## 10. Recommended migration sequence

Ordered so each step is independently shippable and nothing is deleted before it is proven unused.

**Phase 0 — stop the bleeding (no schema change)**

1. Remove `POST /users/{id}/suspend`, `/activate` and `POST /users/{id}/roles`, *or* gate them behind
   a feature flag that returns `501 NOT_IMPLEMENTED` with a message pointing at Keycloak. Removing a
   control that does nothing is not a regression.
2. Add a test asserting a suspended local user is still served — pinning the current behaviour so
   the eventual fix visibly changes it.

**Phase 1 — correlation (the functional gap)**

3. Introduce `IdentityCorrelationStrategy` in `identity/service`, with the ordered policy:
   `keycloak_subject` → `student_number` (from the configured username claim) → verified email.
   Email demoted to last resort.
4. Change the realm so student usernames are student IDs; add a `student_number` claim mapper so
   correlation does not depend on `preferred_username` semantics.
5. On correlation by student number: link the existing `users` row rather than creating a second one.
6. Test: a registrar creates a Student for `202012345`, that student logs in, `/api/v1/students/me`
   returns *their* record. This is the end-to-end behaviour that does not work today.

**Phase 2 — configurable claims**

7. `@ConfigurationProperties("lms.security.claims")` for subject / username / email / given / family
   / roles path. Defaults preserve today's behaviour.

**Phase 3 — integration boundary**

8. Create `identity/integration/keycloak/` with `KeycloakIdentityClient` (admin API),
   `KeycloakUserMapper`, `KeycloakProperties`. No other module may reference it.
9. Define `IdentityProvisioningService` and `StudentProvisioningService` as ports, with the current
   just-in-time behaviour as one adapter, so batch/event/API provisioning can be added later.
10. Reinstate suspension as a real operation that disables the Keycloak account, now that there is
    somewhere for it to live.

**Phase 4 — remove the credential store**

11. Audit first: `SELECT count(*) FROM users WHERE password_hash IS NOT NULL` per environment, and
    confirm which of those rows have a `keycloak_subject`. Rows with a hash and no subject are
    development users who need Keycloak identities before the column can go.
12. Drop `password` from `CreateUserRequest`; stop writing the column; delete
    `User.changePasswordHash` and the `PasswordEncoder` bean.
13. Migration `V16` drops `users.password_hash`.
14. Reframe `POST /api/v1/users` as *administrative provisioning* that goes through the integration
    boundary, or remove it in favour of provisioning alone.

**Phase 5 — surface and observability**

15. Expand `/me`. 16. Write identity events to `audit_events`. 17. Frontend OIDC.

### Sequencing constraint

Phase 4 must not precede Phase 3. Dropping the local credential store while `POST /api/v1/users` is
still the only way to create a person leaves no way to provision anybody. Phase 1 must not wait for
Phase 3 — it is the gap that stops a real student from using the system at all.

---

# Outcome

All phases are implemented. Identity and security tests pass in full.

## What changed

| Was | Now |
|---|---|
| `users.password_hash`, `PasswordEncoder`, password on the create DTO | **Gone** (`V16`). The schema holds no credential and there is no encoder bean. |
| `POST /users/{id}/suspend` set a local flag nothing read | `POST /users/{id}/disable` calls the identity provider. **Verified: login is refused afterwards.** |
| `POST /users/{id}/roles` wrote an inert table | Grants a realm role. **Verified: the next token actually carries it**; revoke removes it. `user_roles` dropped. |
| Login by `student`, `registrar` | Login by **institutional student ID** (`202012345`), asserted as an admin-only `student_number` claim. |
| Correlation by email only | `subject` → **student number** → verified email. Linking is one-time; a second identity claiming a linked account is refused and audited. |
| No provisioning abstraction | `IdentityProvider` port + `identity/integration/keycloak/` adapter, service account with three realm-management roles. |
| Claim names hard-coded | `lms.security.claims.*`, read only by `TokenClaimReader`. |
| `/me`, `/students/me` | A **26-endpoint `/me` family**, none of which accepts an identifier. |
| `audit_events` never written | Identity events recorded; the trail holds no foreign keys (`V27`). |

## Two defects found by testing, not by reading

**A deadlock on the first login of every pre-provisioned student.** Linking updates
`users.keycloak_subject`, which carries a unique index, so PostgreSQL promotes the row lock to
`FOR UPDATE`. The audit write runs in its own transaction so a *refused* link still gets recorded —
and its foreign key to `users` needed `KEY SHARE`, which conflicts. The child blocked on the parent,
the parent waited for the child, and the request thread hung indefinitely. `V27` removes the foreign
key: an audit trail must not hold references into the tables it audits, or writing history contends
with changing it.

**Race recovery ran in a poisoned transaction.** When two first requests from the same person
collide, the loser's insert violates the unique index — after which PostgreSQL refuses every further
statement in that transaction. The recovery read therefore had to move into a fresh one
(`UserProvisioningTransactions`). Only a genuinely concurrent test surfaced this.

Also handled: a token asserting an email that already belongs to an unlinked account no longer fails
the login. The account is created under a reserved-TLD placeholder and the collision is audited,
because locking a legitimately authenticated person out of the portal over a duplicate contact
address is the worse outcome.

## Verified end to end, against real Keycloak

1. Registrar provisions an academic record **by student number**, for someone who has never signed in.
2. An unknown student number is **refused** (`404`), not invented.
3. That student logs in for the first time with their institutional ID.
4. `/api/v1/me/profile` returns **the exact record the registrar created**.
5. Student A gets `403` on B's record, by id, by student number, by listing, and by enrolment filter.
6. Disable → login refused. Enable → login restored.
7. `/api/v1/me/security` returns a **link** to the identity provider, never a password form.

## Still open

- **Lecturer resource scoping.** `TeachingService` exists; grading and section actions are role-gated,
  but "does this lecturer teach *this* section" is not yet enforced everywhere.
- **Frontend OIDC.** The React app still has no authentication of any kind.
- **Production hardening of Keycloak**: `start` not `start-dev`, TLS, real secrets, `sslRequired: all`,
  and removal of the public dev client with its password grant.
