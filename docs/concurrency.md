# Concurrency

The registration window is the defining load event for a student system: most of the university
tries to enrol within the same few minutes, and they compete for the same rows. This document
records how that is handled and, equally importantly, what is deliberately *not* relied upon.

## The rule

> **An application-level check is never the guarantee.** It exists to produce a good error message
> in the common case. The guarantee is always a database constraint or an atomic statement.

Anything of this shape is a lost update waiting to happen:

```java
if (!repository.existsBy(...)) {   // thread A and thread B both pass
    repository.save(...);          // both proceed
}
```

Between the check and the write, another transaction can do anything. Under a registration rush,
"unlikely" becomes "every single time".

## Race 1 — duplicate enrolment

Two requests for the same `(student, section)` arrive together and both pass the existence check.

**Handled by:** a unique index.

```sql
CONSTRAINT uk_enrollments_student_section UNIQUE (student_id, course_section_id)
```

The loser's `INSERT` raises `DataIntegrityViolationException`, which `EnrollmentService` catches and
translates into the *same* `ENROLLMENT_ALREADY_EXISTS` conflict the pre-flight check would have
produced. The client cannot tell which path it took, which is the point.

The same pattern protects `students.student_number`, `students.user_id`, `courses.course_code`, and
`course_sections (course_id, academic_term_id, section_code)`.

## Race 2 — over-filling a section

Two requests both observe a free seat in a section with one seat left. This is the harder case,
because the fix is not a constraint on a single row's uniqueness but a *conditional* update.

**Handled by:** a guarded atomic `UPDATE`.

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query(value = """
    update course_sections
       set enrolled_count = enrolled_count + 1,
           version        = version + 1
     where id = :sectionId
       and status = :openStatus
       and enrolled_count < capacity
    """, nativeQuery = true)
int reserveSeat(UUID sectionId, String openStatus);
```

The predicate and the increment are evaluated inside one statement, so the database holds the row
lock it needs for the write while it tests the condition. Two callers are serialised by the engine;
exactly one sees an affected-row count of `1`. There is no window between deciding and acting.

`version` is incremented explicitly so that an entity instance held elsewhere fails its next
optimistic-lock check rather than silently writing stale state. That matters more than it first
appears: Hibernate writes *every* column on a dirty update, so a stale `CourseSection` flushed later
would otherwise restore its old `enrolled_count` and quietly undo a reservation.

### Why this is native SQL, and not `update versioned`

The natural HQL spelling is `update versioned CourseSection …`, which asks Hibernate to add the
version assignment itself. **Do not reintroduce it.** In Hibernate 6.5 `addVersionedAssignment`
mutates the SQM tree while translating it, and that tree is cached and shared across threads — so
two callers translating this statement at once corrupt a plain `HashMap` inside Hibernate and one of
them dies with `ConcurrentModificationException`.

This is the most contended query in the system, so it reproduced readily: before the change, 3 of 6
runs of `concurrentEnrolmentNeverOverfillsASection` failed with sporadic 500s; after it, 10 of 10
passed. Worth noting what did *not* break — `succeeded` was exactly 10 in every run, failures
included. The guarded `UPDATE` was always correct; the fault was in how Hibernate compiled it.
Native SQL skips SQM translation altogether.

A `CHECK (enrolled_count >= 0 AND enrolled_count <= capacity)` constraint backs this up, so any
future code path that tries to write the counter directly fails loudly instead of over-filling a
room.

### Why a counter rather than `COUNT(*)`

Counting live enrolments on every attempt would be correct but would make the check and the write
two separate operations again. Denormalising the count onto the section is what allows "is there a
seat?" and "take it" to be a single atomic statement. The counter is only ever moved by
`reserveSeat` / `releaseSeat`, never by assignment.

### Why the seat reservation joins the caller's transaction

`CourseCatalog.tryReserveSeat` is annotated `@Transactional(propagation = MANDATORY)`. If it opened
its own transaction, a seat could commit while the enrolment that justified it rolled back —
permanently leaking capacity, with no error anywhere. `MANDATORY` turns that mistake into a
start-up failure instead of a slow leak in production.

The corollary: when `saveAndFlush` fails on the unique index, the whole transaction rolls back and
the seat is returned automatically. **No compensating action is written, and none should be** —
adding one would double-release.

## Optimistic locking

`@Version` is applied where rows are genuinely contended, not universally:

| Entity | Why |
|---|---|
| `CourseSection` | the most contended row in the system during registration |
| `Enrollment` | lifecycle changes race with administrative edits |
| `Student` | registry, advisor and self-service edits arrive from different directions |
| `Course` | catalog edits race with section creation |
| `Assessment` | publication and due-date changes race with submissions |
| `AssessmentAttempt` | submission races with grading |
| `Grade` | the most consequential write in the system; a lost update silently alters a record |

Reference data (`Faculty`, `Department`, `Programme`, `Role`, …) has no `@Version`. Nothing mutates
those rows concurrently, and adding a version column creates a conflict surface for no benefit.

A version conflict surfaces as `OptimisticLockingFailureException`, which the global handler maps to
**409 `CONCURRENT_MODIFICATION`** — telling the client to reload and retry, which is the correct
remedy.

## Idempotency

Ending an enrolment is idempotent. Dropping an already-dropped registration returns `200` and does
**not** release a second seat, because the service checks whether the enrolment was actually holding
one before releasing:

```java
boolean wasHoldingSeat = enrolment.occupiesSeat();
transition(enrolment, target);
if (wasHoldingSeat) { courseCatalog.releaseSeat(...); }
```

A client retrying after a timeout must not be punished for it, and must not corrupt capacity.

## Transaction boundaries

- Transactions begin in the **application service**, never in a controller.
- Read paths are `@Transactional(readOnly = true)`.
- Transactions stay short. No HTTP call, mail send, or object-store upload happens inside one.
- `spring.jpa.open-in-view` is **off**, so lazy loads cannot fire during response serialisation —
  which is how N+1 queries hide from every profiler pointed at the service layer.

## What is tested

`ConcurrentEnrollmentIntegrationTest` runs against real PostgreSQL via Testcontainers, releasing
every thread from a single latch so the requests genuinely overlap:

- **40 applicants, 10 seats** → exactly 10 are enrolled, 30 are waitlisted, the counter
  reads 10, and seated rows agree with the counter. Waitlisted rows do not occupy a seat.
- **16 simultaneous attempts by one student** → exactly one succeeds, and the counter reads 1,
  proving the rolled-back attempts returned their seats.

These use a real database on purpose. Unique-index enforcement, guarded-`UPDATE` semantics and
transaction isolation are precisely the behaviours an in-memory substitute gets wrong, so a green
H2 run would prove nothing.

> Where Docker is unavailable these tests skip rather than fail (`@RequiresDocker`), so a red build
> always means a real defect. `SchemaMigrationConsistencyTest` still verifies mapping/migration
> agreement offline in that environment.
