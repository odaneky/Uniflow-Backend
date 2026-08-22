# Database

PostgreSQL 16. The schema is owned by **Flyway**; Hibernate only ever validates it.

## Ownership and safety

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate   # every environment, without exception
  flyway:
    enabled: true
```

`validate` means the application refuses to start if an entity mapping and the migrated schema have
drifted. That is deliberate: a silent mismatch surfaces later as a corrupt write or a missing
column at 3am, whereas a refused start-up is discovered by whoever caused it.

`create` / `create-drop` / `update` must never be used. In production `spring.flyway.clean-disabled`
is `true`, so migrations are forward-only.

## Migrations

`src/main/resources/db/migration`, one per module so a change stays legible:

| File | Contents |
|---|---|
| `V1__identity.sql` | `users`, `roles`, `permissions`, `role_permissions`, `user_roles` |
| `V2__academic.sql` | `faculties`, `departments`, `programmes`, `academic_years`, `academic_terms` |
| `V3__student.sql` | `student_profiles`, `students` |
| `V4__course.sql` | `courses`, `course_sections` |
| `V5__enrollment.sql` | `enrollments` |
| `V6__learning.sql` | `course_contents`, `learning_modules`, `lessons`, `learning_materials` |
| `V7__assessment.sql` | `assessments`, `assessment_attempts` |
| `V8__grading.sql` | `grade_scales`, `grade_scale_bands`, `grades` |
| `V9__attendance.sql` | `attendance_records` |
| `V10__communication.sql` | `announcements`, `conversations`, `conversation_participants`, `messages` |
| `V11__notification.sql` | `notifications` |
| `V12__document.sql` | `documents` |
| `V13__administration.sql` | `audit_events` |
| `V14__seed_reference_data.sql` | roles, permissions, default grade scale |

Applied migrations are immutable. Fix a mistake with a new migration, never by editing an old one —
Flyway checksums them, and a rewritten file breaks every environment that already ran it.

## Conventions

- **Primary keys** — `uuid`, assigned by the application (see `BaseEntity`), never sequential.
  Random ids do not leak volume or ordering, and matter when identifiers appear in URLs.
- **Timestamps** — `timestamptz`, always. A university spans time zones and daylight-saving
  transitions; a naive timestamp makes "when was this grade changed?" ambiguous twice a year.
- **Audit columns** — every table carries `created_at`, `updated_at`, `created_by`, `updated_by`,
  populated by Spring Data auditing from the security context.
- **Enums** — stored as `varchar`, not native enums or ordinals. An ordinal silently reinterprets
  every existing row when a constant is inserted in the middle.
- **Money/marks** — `numeric(p,s)`. Never floating point.
- **Naming** — `snake_case`; tables plural; `uk_` unique, `fk_` foreign key, `ck_` check,
  `idx_` index.

## Constraints that carry real weight

These are not decoration; the application relies on them for correctness under concurrency
(see `concurrency.md`).

```sql
-- Prevents duplicate enrolment when two requests race.
CONSTRAINT uk_enrollments_student_section UNIQUE (student_id, course_section_id)

-- Backstops the seat counter maintained by the guarded UPDATE.
CONSTRAINT ck_course_sections_enrolled CHECK (enrolled_count >= 0 AND enrolled_count <= capacity)

-- One student record per account, one matriculation number per student.
CONSTRAINT uk_students_user           UNIQUE (user_id)
CONSTRAINT uk_students_student_number UNIQUE (student_number)

-- A half-open registration window would be ambiguous exactly when it matters.
CONSTRAINT ck_academic_terms_window CHECK (
    (registration_opens_at IS NULL AND registration_closes_at IS NULL)
    OR (registration_opens_at IS NOT NULL AND registration_closes_at IS NOT NULL
        AND registration_closes_at > registration_opens_at))
```

Two partial indexes earn their place:

```sql
-- At most one overall (non-assessment) grade per student per section.
CREATE UNIQUE INDEX uk_grades_student_section_overall
    ON grades (student_id, course_section_id) WHERE assessment_id IS NULL;

-- The dispatcher only ever scans the pending backlog, a tiny slice of the table after a term.
CREATE INDEX idx_notifications_pending ON notifications (created_at) WHERE status = 'PENDING';
```

## Indexing

Indexed because they are looked up by, joined on, or filtered by:

`users(username, email, status)` · `students(student_number, user_id, programme_id, status)` ·
`courses(course_code, department_id, status)` ·
`course_sections(course_id, academic_term_id, lecturer_user_id)` ·
`enrollments(student_id, course_section_id, status)` ·
`attendance_records(course_section_id, session_date)` ·
`messages(conversation_id, sent_at)` · `audit_events(entity_type, entity_id)`

## Foreign keys across module boundaries

A cross-module reference is a `UUID` column in Java but still a real foreign key in the database:
the boundary is an application concern, integrity is not negotiable.

Delete behaviour is chosen per relationship rather than by habit:

- `ON DELETE CASCADE` where the child is meaningless without its parent — lessons under a module,
  participants in a conversation.
- `ON DELETE SET NULL` for optional references such as a section's lecturer.
- **No cascade** on academic records. Deleting a student must not silently erase their enrolments
  and grades; `audit_events.actor_user_id` is `SET NULL` for the same reason — removing a user must
  not erase the history of what they did.

## Avoiding N+1

- `spring.jpa.open-in-view: false`, so a lazy load cannot fire during serialisation.
- Every association is `LAZY`.
- Fetch joins where a parent is genuinely always needed:
  `CourseSectionRepository.findByIdWithCourse`, `UserRoleRepository.findAllByUserIdWithRole`.
- Filtered searches are a single query with null-disabled predicates rather than a family of
  finder methods.

## Verifying the schema

Two layers, so drift cannot ship:

1. **`SchemaMigrationConsistencyTest`** — builds Hibernate's metadata model against the PostgreSQL
   dialect with no connection, and diffs the tables and column types it expects against the ones
   the migrations create. Runs anywhere, including machines without Docker.
2. **`ApplicationBootstrapIntegrationTest`** — starts the whole application against real PostgreSQL
   via Testcontainers. Because `ddl-auto` is `validate`, simply reaching a started context proves
   the migrations applied and the schema satisfies every mapping.

## Local development

```bash
docker compose up -d          # PostgreSQL on :5432
./mvnw spring-boot:run
```

Defaults: database `university_lms`, user `lms`, password `lms`. Overridable via `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`. The `prod` profile supplies no defaults at all, so a misconfigured
deployment fails at start-up rather than connecting somewhere unintended.

## `users.keycloak_subject`

Added by `V15__link_users_to_identity_provider.sql`. It holds the identity provider's `sub` claim
and is the only thing tying a bearer token to a row in this database — without it an authenticated
caller cannot be resolved to a person, and no ownership check is possible.

The column is **unique but nullable**. PostgreSQL permits many NULLs in a unique index, so rows
created before the identity provider existed stay valid while no two users can claim the same
subject. That guarantee has to be the database's: just-in-time provisioning means two concurrent
first requests from the same person race to insert, and an application-level check would be a lost
update. The application catches the violation and re-reads, which is how the loser learns the
answer — the index is what makes the answer correct.

The same migration drops `NOT NULL` from `password_hash`, because a provisioned user has no local
password and cannot: Keycloak holds the credential.
