# Modules

Ownership, dependencies, and current implementation depth.

## Dependency graph

Arrows point to the module being depended upon. Every arrow crosses a published `api/` contract;
there are no cycles.

```
                 ┌────────────┐
                 │  identity  │◄──────────────┐
                 └─────▲──────┘               │
                       │                      │
   ┌──────────┐   ┌────┴─────┐          ┌─────┴──────┐
   │ academic │◄──┤ student  │          │   course   │
   └────▲─────┘   └────▲─────┘          └─────▲──────┘
        │              │                      │
        │              │  ┌────────────┐      │
        └──────────────┴──┤ enrollment ├──────┘
                          └─────┬──────┘
                                │
                          ┌─────▼──────┐
                          │ curriculum │
                          └────────────┘
```

`common` is depended on by everything and depends on nothing.

## Ownership

| Module | Owns | Published contract | Depth |
|---|---|---|---|
| `common` | error model, `BaseEntity`, auditing, security config, pagination | — | complete |
| `identity` | `User`, `Role`, `Permission`, `UserRole` | `UserDirectory`, `CurrentUserProvider` | REST, DTOs, service; authentication delegated to Keycloak |
| `academic` | `Faculty`, `Department`, `Programme`, `AcademicYear`, `AcademicTerm` | `AcademicStructure` | REST, DTOs, two services (structure, calendar) |
| `student` | `Student`, `StudentProfile` | `StudentDirectory` (incl. `studentIdOfUser`) | **full vertical slice** — REST, DTOs, service, tests |
| `course` | `Course`, `CourseSection` | `CourseCatalog` | **full vertical slice** — REST, DTOs, service, seat reservation |
| `enrollment` | `Enrollment` | `EnrollmentDirectory` | **full vertical slice** — REST, DTOs, service, concurrency tests, roster |
| `learning` | `CourseContent`, `LearningModule`, `Lesson`, `LearningMaterial` | — | REST + `/me` reads; staff writes |
| `assessment` | `Assessment`, `AssessmentAttempt` | — | REST for assessments (attempts still entity-only) |
| `grading` | `GradeScale`, `GradeScaleBand`, `Grade` | `AcademicRecord` | Award/publish, section gradebook, `/me/grades`, `/me/academic-summary` |
| `attendance` | `AttendanceRecord` | — | entities + repositories |
| `communication` | `Announcement`, `Conversation`, `ConversationParticipant`, `Message` | — | Announcements, conversations, `/me` reads |
| `notification` | `Notification` | — | REST create + `/me/notifications` |
| `document` | `Document` | — | Metadata register + `/me/documents` (no file bytes) |
| `finance` | `StudentAccount`, `AccountEntry` | — | Thin ledger; `/me/account` |
| `request` | `ServiceRequest` | — | Student `/me/requests` and registry decide |
| `curriculum` | `ProgrammeRequirementBlock` | `CurriculumCatalog` | Requirement blocks; `/me/degree-progress` |
| `administration` | `AuditEvent` | `AuditTrail` | REST list + append-only writes from identity, enrolment, catalog, grading |

"Entities + repositories" still describes `attendance`: the schema and
boundaries exist, but no service or REST layer has been written yet. Learning, assessment,
grading, communication, notification, document, finance, student requests and degree progress
now have staff writes and `/me` reads so a seeded student account can be demonstrated
end-to-end.

`identity` and `academic` moved past that line for a specific reason: without endpoints for users,
faculties, departments, programmes, years and terms, **the API could not be bootstrapped through
the API** — every id a student or section refers to had to be inserted with hand-written SQL. They
carry write operations but no service layer of their own beyond that.

`identity` does not authenticate anyone. Keycloak owns credentials and issues the tokens the
application validates (`common/security/`), so an `identity` user row is a profile and local
authorization record, not a login. Its `password_hash` column predates that decision and is now
dormant — see ROADMAP P0.1.

It does own the **join** between the two, though, and publishes it as `CurrentUserProvider`. That is
what lets other modules ask ownership questions without reading `users`: `enrollment` establishes
whose enrolment it is by asking `identity` who the caller is and `student` which record is theirs
(`StudentDirectory.studentIdOfUser`), never by joining across module tables.

`academic` is split into two services deliberately. `AcademicStructureService` maintains the
Faculty → Department → Programme hierarchy, which administrators edit rarely;
`AcademicCalendarService` maintains years and terms, which the registry manages every term. They
change for different reasons, so they are separate classes.

## Notes on specific modules

### `common`
Cross-cutting infrastructure only. **No business logic may be added here.** The test of whether
something belongs is whether at least two unrelated modules need it *and* it encodes no domain
rule. A helper that knows what a "student" is does not belong in `common`.

### `student` ↔ `identity`
`Student` does not duplicate name or email. Those belong to `User` and are resolved through
`UserDirectory`. A student's *academic* standing (`StudentStatus`) is separate from their *account*
standing (`UserStatus`): a graduated student keeps a usable account for transcript access.

### `course`
`Course` is the durable catalog definition; `CourseSection` is a specific offering in a specific
term (an **occurrence** in the product — see [glossary](glossary.md)). Students enrol in sections, never in courses. Conflating them makes it impossible to
represent the same course taught twice, which is the normal case.

### `enrollment`
Depends on four other modules (`student`, `course`, `academic`, `curriculum`) and owns no reference data of its own. It is the module where the
concurrency strategy is actually exercised — see `concurrency.md`.

### `learning`
`LearningModule` is named that way because `java.lang.Module` is implicitly imported everywhere; a
domain class called `Module` shadows it.

### `document`
Stores **metadata only**. `storageKey` points into an object store (S3/GCS/Azure/MinIO). File bytes
must never be written to PostgreSQL: it inflates every backup, drags multi-megabyte payloads through
the connection pool the rest of the system depends on, and makes point-in-time recovery painful.

### `notification`
Rows are written inside the originating transaction and delivered afterwards by a dispatcher. No
notification is ever sent synchronously from a controller — that would place a network call on the
critical path of a commit, and a mail-server failure would roll back the enrolment that triggered it.
