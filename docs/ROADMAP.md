# Remaining Work

What exists today, what is missing, and in what order it should be picked up.

Phase 1 delivered the **foundation**: module boundaries, the domain model, migrations, the error
contract, and three complete vertical slices (student, course, enrollment). It deliberately did not
implement every capability. This document is the honest inventory of the gap.

---

## Status at a glance

| Area | State |
|---|---|
| Modular monolith structure, 14 modules | ✅ complete |
| Domain model — 32 entities, 30 repositories | ✅ complete |
| Flyway migrations (14) + seed reference data | ✅ complete, schema-verified |
| Error contract + global handler | ✅ complete |
| Auditing, pagination, optimistic locking | ✅ complete |
| Student / Course / Enrollment REST slices | ✅ complete |
| Concurrency-safe enrolment | ✅ implemented **and verified** against real PostgreSQL |
| **Authentication & authorization** | ✅ Keycloak + resource server, role rules enforced and tested |
| Resource-scoped (owner) authorization | ✅ reads and writes, pinned by tests |
| Reference-data endpoints (users, faculties, terms…) | ✅ complete — the API now bootstraps itself |
| 8 foundational modules (learning, assessment, …) | ⚠️ entities + repositories only |

---

## P0 — Blockers before any reachable deployment

### 1. ~~Authentication and authorization~~ — done

The application is a **resource server**: it validates Keycloak-issued JWTs and authenticates
nobody itself. `/api/v1/**` is `authenticated()`; the `TODO(auth)` line is gone.

| Piece | Where |
|---|---|
| Token validation — signature, issuer, **and audience** | `SecurityConfig.jwtDecoder` + `AudienceValidator` |
| `realm_access.roles` → `ROLE_*` authorities | `KeycloakRealmRoleConverter` |
| Coarse role rules, one ordered auditable list | `SecurityConfig.filterChain` |
| 401/403 in the standard error envelope | `SecurityErrorResponder` |
| Realm, clients and dev users | `docker/keycloak/` |

Three decisions worth knowing:

- **Audience is validated, not just issuer.** Every client in the realm shares an issuer, so
  issuer-only acceptance turns any Keycloak client added later into a way in.
- **The decoder is built from the JWK set URI, not by issuer discovery.**
  `JwtDecoders.fromIssuerLocation` fetches during bean creation, which makes Keycloak a hard
  start-up dependency — a brief outage during a deploy would stop the application booting. The
  issuer is still validated; that check just no longer needs a network call to configure.
- **`SecurityErrorResponder` is registered twice, on purpose.** See the defect below.

### Owner-scoping — done

Role rules could never answer "is this record yours": a URL pattern does not know whose data is
behind it. The missing piece was not a check but a **fact** — nothing linked a token to a row, so
an authenticated caller was anonymous to the domain.

`V15__link_users_to_identity_provider.sql` adds `users.keycloak_subject` (unique, nullable) and
drops `NOT NULL` from `password_hash`. `CurrentUserProvider` resolves a token's `sub` to a user,
provisioning one just-in-time on first sight, and `CurrentUser.requireSelfOrStaff` is the guard the
services call.

| Endpoint | Before | Now |
|---|---|---|
| `GET /students/{id}` and `/by-number/{n}` | any student could read any student | own record, or staff |
| `GET /students` | listed the whole institution | `403` for students; staff list, students use `/students/me` |
| `POST /enrollments` | a student could enrol **anyone** by naming them in the body | own student record, or staff |
| `POST /enrollments/{id}/drop`, `/withdraw` | a student could drop **another student** out of a course | own enrolment, or staff |
| `GET /enrollments/{id}` | any enrolment | own, or staff |
| `GET /enrollments` | unfiltered | forced to the caller for students |
| `GET /me`, `GET /students/me` | did not exist | resolve the caller to a domain identity |

The write-side holes were the sharper ones, and were demonstrated against a running instance before
being fixed. `OwnerScopingIntegrationTest` (13 tests) was written first and failed on nine of them.

Incidentally fixed: `created_by` records the token's subject, which now joins to
`users.keycloak_subject` instead of being an unresolvable UUID.

### What is still missing

- **Retire the local password store.** `password_hash` is now nullable and never written by
  provisioning, but `POST /api/v1/users` still requires and hashes a password that nothing checks,
  and creates accounts nobody can log in as — Keycloak does not know them. It should provision into
  Keycloak's admin API, or be dropped in favour of just-in-time provisioning alone; then the column
  and `PasswordEncoder` should go in a migration.
- **Just-in-time provisioning opens a second transaction.** `UserProvisioningService` is
  `REQUIRES_NEW`, so a caller's very first request holds two connections at once. Resolution is
  cached per request, which bounds it to once per request rather than once per check, but a burst of
  *first-ever* requests can still exhaust the pool — this was reproduced: forty concurrent
  first-time callers against a twenty-connection pool failed with
  `CannotCreateTransactionException`. Resolving the caller in a filter, before the business
  transaction opens, would remove the nesting entirely.
- **Lecturer scoping.** `POST /enrollments/{id}/complete` is restricted to `LECTURER`, `REGISTRAR`
  and `SYSTEM_ADMIN` by role, but nothing checks the lecturer teaches *that* section. The same is
  true of every future grading endpoint. Needs a `teaches(lecturerUserId, sectionId)` contract from
  the course module.
- **Production hardening of Keycloak** — `start` rather than `start-dev`, TLS, a fixed hostname,
  real admin credentials from a secret store, `sslRequired: "all"`, and removal of the public dev
  client with its password grant.

### Defect found while wiring this up

**Every invalid token returned a 401 with an empty body**, and a `WWW-Authenticate` header naming
the exact decode failure.

`oauth2ResourceServer` installs its own `BearerTokenAuthenticationEntryPoint`, which takes
precedence over the one registered on `exceptionHandling` whenever the failure originates in the
bearer-token filter — that is, for every malformed, expired or wrong-audience token, which is
nearly all real authentication failures. Only a request with *no* `Authorization` header at all
took the path that worked, and that is exactly the case a quick manual check tries first.

Two consequences, both bad: clients that branch on `code` got nothing to branch on, on the response
they are most likely to have to handle; and the header handed an unauthenticated caller an oracle
for distinguishing "expired" from "bad signature" while probing. Fixed by registering the responder
inside `oauth2ResourceServer` as well, and by emitting a bare `WWW-Authenticate: Bearer`. Pinned by
`StudentControllerTest.invalidTokenIsUnauthorisedInTheStandardShape` and by folder 07 of the
Postman collection.

### 2. ~~Verify the concurrency tests actually pass~~ — done

Run against real PostgreSQL. The full suite is **75 tests, 0 skipped**, and
`concurrentEnrolmentNeverOverfillsASection` was executed 10 consecutive times with an identical
result (`succeeded=10 rejectedAsFull=30`). The design holds: 40 simultaneous applicants for 10
seats fill exactly 10, and the counter agrees with the row count.

Doing this found two real defects that no amount of unit testing would have surfaced — see
*Defects found by running them* below.

**If Testcontainers cannot reach your Docker daemon** (it could not reach Docker Engine 29 here —
the bundled docker-java fails `/info` negotiation and every integration test silently *skips*),
point the suite at a database you already have:

```bash
docker compose up -d
docker compose exec -T postgres psql -U lms -d postgres -c 'CREATE DATABASE university_lms_test;'
./mvnw verify -Dlms.test.datasource.url=jdbc:postgresql://localhost:5432/university_lms_test
```

Use a dedicated database — Flyway migrates whatever it is given. Upgrading Testcontainers so the
default path works on Docker 29 is still worth doing; the escape hatch is not a substitute for CI
running the normal way.

### 3. ~~Reference-data endpoints~~ — done

The identity and academic modules now expose REST endpoints, so **the whole system can be
bootstrapped through the API** with no SQL step:

| Endpoint | Notable behaviour |
|---|---|
| `POST /api/v1/users` + `/{id}/activate`, `/suspend`, `/roles` | password is hashed with BCrypt and never echoed back — `UserResponse` has no such field, and validation errors redact the rejected value |
| `POST /api/v1/faculties`, `/departments`, `/programmes` | codes normalised to upper case so `comp` and `COMP` cannot become two departments |
| `POST /api/v1/academic-years`, `/academic-terms` | date ordering validated in the service, so the caller is told *which* date is wrong rather than getting a generic constraint violation |
| `PUT /api/v1/academic-terms/{id}/registration-window` | its own action, not a general update: opening registration is what lets students start competing for seats, and it deserves to be individually auditable |

Cross-module references are checked through `identity.api.UserDirectory` rather than by reading
identity's tables — assigning a dean or department head to a non-existent user is a 404, not a
foreign-key error.

`docs/postman/bootstrap-reference-data.sql` is now an **optional shortcut** (fixed ids, useful when
you want the same ids every run), not a prerequisite.

**Still untested at the JUnit level.** These endpoints are covered end-to-end by the Postman
collection — a full run against an empty database is 86 requests and 161 assertions green — but the
Java suite does not cover them. Service-level tests for the validation branches
(`INVALID_DATE_RANGE`, the both-ends-or-neither window rule, code normalisation, the `UserDirectory`
checks) are the obvious next increment; see the test-gap table below.


---

## Defects found by running the integration tests

Both were live in `main` and neither was reachable by a unit test. Kept here as the argument for
why the integration suite must stay runnable.

### `GET /api/v1/courses` returned 500 on every unfiltered request

`ERROR: function lower(bytea) does not exist` (SQLState 42883). The catalog search filter was
written as `:search is null or ... like lower(concat('%', :search, '%'))`. Neither a bare null-check
nor HQL's variadic `concat` constrains a parameter's type, so Hibernate could not infer one, fell
back to its serializable mapping, and bound the value as `bytea`.

Only the **null** case failed, which is why it went unnoticed: passing an actual search term gave
Hibernate a type. The default unfiltered listing — by far the most common request — was the broken
path. Fixed by building the complete LIKE pattern in `CourseService` and binding it beside `like`,
where the operand type is unambiguous. LIKE metacharacters in a user's term are now escaped too, so
searching `50%` no longer matches the entire catalog.

### ~1 request in 40 failed with a 500 under concurrent enrolment

`java.util.ConcurrentModificationException` thrown from *inside* Hibernate, via
`addVersionedAssignment` → `AbstractSqmPath.resolvePath` → `HashMap.computeIfAbsent`.

`update versioned` asks Hibernate to add the `@Version` assignment during translation, and it does
so by mutating the SQM tree — which Hibernate caches and shares across threads. Two callers
translating it simultaneously corrupt that shared map. The seat-reservation statement is the single
most contended query in the system, so this was reliably reproducible: 3 of 6 runs failed before
the fix, 10 of 10 pass after.

Notably the *invariant never broke* — `succeeded=10` held in every run, including failing ones. The
guarded `UPDATE` was always correct; the fault was in how Hibernate compiled it. Fixed by expressing
the statement as native SQL, which bypasses SQM translation and still increments the version.

## P1 — Completing the core academic flow

### Enrolment rules not yet enforced

The live checks are: student eligible, occurrence open, registration/add-drop window, financial hold,
duplicate, course prereqs/coreqs/minimum level, **max** credit load, timetable clash, lecture →
tutorial → lab order, programme curriculum, waitlist when full, and **min** credit load at checkout
(`POST /enrollments/checkout`). Remaining:

- Waitlist **position** and notifications when a seat is offered.
- Staff override of a curriculum/clash refusal.

### Programme curriculum
`Programme` records `totalCredits` but not *which* courses satisfy it. Needed for prerequisites,
degree audit, and progression. Note the modelling constraint recorded in `architecture.md`:
core-vs-elective is a property of the **programme's requirement block**, never of the course.

### `Submission`
`AssessmentAttempt` exists, but there is no submission artefact linking an attempt to an uploaded
`Document`. This is the first thing to add when assessment work begins.

### Grade computation
`Grade` and `GradeScale` exist; nothing computes a grade from attempts, applies `weightPercent`, or
derives a GPA. Keep the computation in `grading` — the decoupling from `assessment` is deliberate.

---

## P2 — Service and REST layers for the foundational modules

Each has entities, repositories and migrations; none has a service, DTOs or controllers.

| Module | Notable work beyond plain CRUD |
|---|---|
| `learning` | publish/unpublish, ordering, material ↔ document linkage |
| `assessment` | attempt numbering, late detection, submission windows |
| `grading` | band resolution, moderation workflow, transcript projection |
| `attendance` | bulk register submission, attendance-percentage queries |
| `communication` | audience resolution (a section announcement must follow the live enrolment list) |
| `notification` | **the dispatcher** — see below |
| `document` | object-store integration (S3/GCS/Azure/MinIO), pre-signed URLs, virus scanning |
| `administration` | emitting audit events from services; retention policy |

### Notification dispatcher
Rows are written durably in the originating transaction; nothing delivers them yet. Needed: a
scheduled or queue-driven worker draining `status = 'PENDING'` (the partial index
`idx_notifications_pending` already exists), with retry and dead-lettering. **Do not send from a
controller** — that puts a network call on the critical path of a commit.

---

## P3 — Operational maturity

- **CI pipeline** — build, test, and the Testcontainers suite on every PR. There is no CI config yet.
- **Idempotency keys** on `POST` endpoints so a client retry after a timeout cannot double-enrol.
- **Rate limiting**, particularly on the registration endpoints during a rush.
- **Observability** — ~~metrics via Actuator with nothing scraping them~~ **done for this slice**:
  Micrometer Observation → OTLP → collector → Grafana LGTM (Prometheus, Tempo, Loki). Compose
  profile `obs`. Custom `uniflow.enrolment` / `occurrence` / `grade` counters. Remaining: Java
  agent, Grafana Cloud / Mimir HA, browser SDK, tail sampling.
- **OpenAPI** — `springdoc-openapi` would generate live docs from the existing controllers and DTOs.
- **Read replicas / caching** — only when a measured need appears. Deliberately not pre-emptive.
- **Load testing** the registration window against realistic concurrency.
  Starter: `npm run load:smoke` / `scripts/load_smoke.py` (default ~2000 RPS read-heavy;
  `load:smoke:5k` for ~5000; `--scenario registration` for catalog/registration GETs).
  Not a full enrolment rush yet.

---

## Known gaps in the test suite

The suite is green (**118 tests, all run**) but still uneven. Honest inventory:

| Gap | Notes |
|---|---|
| No `CourseService` unit test | `StudentService` and `EnrollmentService` are covered; course has integration coverage of search only. |
| No repository tests (`@DataJpaTest`) | Custom queries — `search`, `reserveSeat`, `findByIdWithCourse` — are exercised only indirectly. |
| Controller tests cover `student` only | `CourseController` and `EnrollmentController` have no `@WebMvcTest`. |
| No dedicated optimistic-locking test | `@Version` is configured and mapped, but no test forces a version conflict and asserts the `409 CONCURRENT_MODIFICATION` response. |
| ~~Integration tests unrun~~ | Resolved — 75 tests, 0 skipped, verified against real PostgreSQL. |
| No architecture-enforcement test | The module-boundary rule is currently verified by a shell grep, not by the build. ArchUnit would make it a build failure. |
| **Reference-data endpoints have no JUnit coverage** | `UserService`, `AcademicStructureService` and `AcademicCalendarService` are exercised only by the Postman collection (86 requests / 161 assertions, green from an empty database and idempotent on re-runs). That proves the happy path and the error envelope; it does not run in CI. Highest-value gap now. |
| No test decodes a real Keycloak token | `AudienceValidator` and `KeycloakRealmRoleConverter` are unit-tested and the rules are integration-tested with a stubbed principal, but nothing verifies signature checking end to end. A test signing a token with a local RSA key against a stub JWKS endpoint would close it. |

---

## Deliberately not done

Per the brief, and worth restating so nobody "fixes" these by accident:

- **No microservices.** The modular monolith is the design; extraction is possible later precisely
  because modules talk through `api/` contracts.
- **No Kafka**, until there is a concrete event-driven requirement.
- **No Redis**, until there is a measured caching need.
- **No file bytes in PostgreSQL** — `document` stores metadata and a storage key only.
- **No premature async.** Notifications are persisted synchronously and delivered out of band; that
  is the correct boundary, not an omission.

---

## Suggested order

1. ~~**P0.2** — confirm the concurrency design holds.~~ Done, and it found two real defects.
2. ~~**P0.3** — reference-data endpoints, so the system stops depending on hand-written SQL.~~ Done.
3. ~~**P0.1** — authentication and authorization, including resource-scoped checks.~~ Done.
4. **Retire the local password store**, and resolve the caller in a filter so provisioning stops
   nesting transactions. Both are small, and both are loose ends left by the identity work.
5. **P1** — curriculum model, then prerequisites and credit limits.
6. **P2/P3** — in whatever order product priority dictates.

There is no longer a single blocking item: the system authenticates, authorises by role, and scopes
by ownership, all under test. What remains before a real deployment is hardening — Keycloak in
production mode, lecturer-level scoping, and the loose ends in step 4 — rather than missing
capability.

---

## Deferred — later product work

Shipped enough to demo; not scheduled. Do not implement until product picks a rule set.

### Academic advisor auto-matching

**Today:** a registrar (or admin) manually assigns one `ACADEMIC_ADVISOR`-roled staff member to each
student (`PATCH /students/{id}` with `advisorUserId`). Lecturers cannot advise without that role.

**Not yet:** automatic who-advises-whom matching. Candidates for a later design (pick one campus
policy before coding):

- By programme / department / year of study
- By cohort or admission intake
- Load-balanced across advisors (cap advisees per advisor)
- Suggested match with registrar confirmation (soft auto) vs hard auto on admission/provision

Until then, assignment stays manual. UI copy and seed data should not imply matching is live.
