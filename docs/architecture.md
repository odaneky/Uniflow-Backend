# Architecture

The University LMS backend is a **modular monolith**: one deployable Spring Boot application whose
internals are divided into business modules with enforced boundaries.

> This document covers the Java backend under `java/`. The React prototype at the repository root
> is a separate front-end artefact and is not described here.

## Why a modular monolith

A university has perhaps a dozen genuinely distinct capabilities and one transactional core:
enrolling a student touches student records, course capacity, and registration windows *together*,
and must either happen completely or not at all. Splitting that across services would replace a
database transaction with a distributed saga — a large amount of machinery bought before there is
any load, team, or deployment pressure that would justify it.

So the system is one process, but it is not one undifferentiated ball of code. Every module owns
its data and exposes a narrow contract, which is what makes extraction *possible later* without
paying for it now.

## Module boundaries

```
com.university.lms
├── common          cross-cutting only: errors, auditing, security, pagination
├── identity        users, roles, permissions
├── academic        faculties, departments, programmes, years, terms
├── student         student records and profiles
├── course          catalog courses and their term-specific sections
├── enrollment      registration of students into sections
├── learning        course content: modules, lessons, materials
├── assessment      assessed work and attempts
├── grading         grade scales and awarded grades
├── attendance      attendance registers
├── communication   announcements, conversations, messages
├── notification    outbound notifications
├── document        file metadata (never file bytes)
└── administration  audit history
```

Each module is laid out the same way:

```
module/
├── domain/       entities, enums, module-owned error codes
├── repository/   Spring Data interfaces — module-internal
├── service/      application services; the transaction boundary
├── web/          REST controllers
├── dto/          request/response records
└── api/          the module's published contract for other modules
```

## The rule that holds it together

**A module may not touch another module's internals.** Concretely:

- `student` must never inject `course.repository.CourseSectionRepository`.
- It may depend on `course.api.CourseCatalog`.

Published contracts live in `api/`; the implementing adapter lives in `service/` and is the only
class that bridges the two. There are five such contracts today:

| Contract | Owner | Answers |
|---|---|---|
| `identity.api.UserDirectory` | identity | does this user exist, who are they |
| `academic.api.AcademicStructure` | academic | does this programme/department/term exist, is registration open |
| `student.api.StudentDirectory` | student | does this student exist, may they enrol |
| `course.api.CourseCatalog` | course | section details, **take a seat**, **release a seat** |
| `administration.api.AuditTrail` | administration | append-only record of consequential actions |

Note what `CourseCatalog` exposes: *behaviour*, not data. Enrolment asks the course module to
reserve a seat and is told yes or no. It never reads a capacity, decides for itself, and writes a
counter back. Keeping the decision inside the module that owns the row is what makes it safe under
concurrency — and is what would survive that module moving behind a network call.

## Cross-module references

Associations follow the same boundary:

- **Within a module** — a real JPA association. `CourseSection` has a `@ManyToOne Course`.
- **Across modules** — a plain `UUID` column plus a database foreign key. `Student.programmeId`
  is a `UUID`, not a `@ManyToOne Programme`.

This costs a little navigational convenience and buys three things: no compile-time dependency
between module domains, no accidental lazy-load that drags another module's entity graph into a
query, and referential integrity still enforced by the database. Every such field is commented in
the entity as a cross-module reference.

## Layering

```
Controller  ──►  Application Service  ──►  Repository
   bind,            business rules,          persistence
   validate,        transaction boundary
   map to HTTP
```

Controllers never inject repositories. Transactions never begin in a controller. Business rules
never live in a DTO.

## Security posture

`common.security.SecurityConfig` establishes the shape: stateless sessions, no form login or CSRF
token flow to unwind later, method security enabled, BCrypt in place.

Authorization is layered. **Role rules** live in `SecurityConfig` as one ordered list — coarse,
auditable, and answering only "may this kind of user call this kind of endpoint". **Ownership**
lives in the service layer, where the entity is in hand: a URL pattern cannot know whose record is
behind an id, so `GET /api/v1/students/{id}` is indistinguishable from itself whoever owns it.

Ownership needs a fact that a token does not carry. Keycloak's `sub` names nothing in this database,
so before `users.keycloak_subject` existed an authenticated caller was **anonymous to the domain** —
the application knew a student was calling but not which, and could not have checked ownership even
if asked to. `CurrentUserProvider` is that join, and `CurrentUser.requireSelfOrStaff` is the guard
the services call.

**Keycloak is the identity provider** and runs in `docker-compose.yml`
(`docker/keycloak/`), with a realm whose roles are exactly the six names in `SecurityRoles`. The
application is a resource server: it validates tokens and authenticates nobody itself, so there is
no login endpoint, no session, and no password check in the application at all.

That settles a question the current code leaves open: **who owns credentials.** Today
`identity` stores a BCrypt `password_hash` and `SecurityConfig` exposes a `PasswordEncoder`. With
an external IdP, Keycloak owns credentials and authentication, and the local `users` table becomes
a *profile and authorization* record keyed by the token's `sub` — it still answers "who is this
person in this university" (their student record, their department, their roles as this system
understands them), but it stops answering "is this password correct". Those are different
questions, and conflating them is how systems end up with two sources of truth for identity that
drift.

That split is now implemented. A `users` row is keyed to the identity provider by
`keycloak_subject`, provisioned just-in-time on first sight of a token, and holds profile and local
authorization only. `password_hash` is nullable and never written by provisioning; it is dormant and
should be removed rather than left looking authoritative. Tracked in ROADMAP P0.1.

Roles are read from the token, not from `user_roles`. The identity provider is authoritative for
what someone may do; reading the local table as well would create a second answer free to drift
from the first.

Authorization beyond role checks is resource-scoped and belongs in the service layer, never in a
client-supplied flag.

## Error handling

Every failure leaves the application as one `ApiErrorResponse` (see `api-guidelines.md`). Two
invariants:

1. **Nothing internal escapes.** No stack traces, SQL, constraint names, or class names reach a
   client. They are logged against the same `traceId` the client receives.
2. **Codes are stable.** Clients branch on `code`; `message` is prose and may change. Each module
   owns its own `ErrorCode` enum, so error vocabulary stays with the module that defines it rather
   than piling up in `common`.

## Deliberate deviations from the brief

| Decision | Reason |
|---|---|
| Entity named `LearningModule`, not `Module` | `java.lang.Module` is auto-imported into every compilation unit; a domain type called `Module` shadows it and produces confusing errors in unrelated code. |
| Course has `components` (`LECTURE`/`TUTORIAL`/`LABORATORY`), not a single `CourseType` | A taught course is not "a lecture" — it commonly includes lecture and tutorial, sometimes lab. Core vs elective still belongs on the programme, not the course. |
| `@Version` on selected entities only | Optimistic locking is applied where rows are genuinely contended (see `concurrency.md`), not universally. Adding it to reference data creates a write-conflict surface on rows nothing concurrently mutates. |
| Ids assigned in Java, entities implement `Persistable` | Makes `equals`/`hashCode` correct before persistence — the usual JPA trap — without the redundant `SELECT` before every `INSERT` that assigned ids would otherwise cause. |
