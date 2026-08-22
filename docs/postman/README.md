# Postman collection

`University-LMS.postman_collection.json` — **86 requests across all 45 endpoints**, chained by
collection variables so a top-to-bottom run builds a working university from an empty database:
a lecturer account, a faculty, a department, a programme, an academic year, a term with its
registration window open, a course, a section, a student, and an enrolment.

## Import

1. Postman → **Import** → both files in this directory.
2. Select the **University LMS — Local** environment (top right).

The collection also carries defaults for every variable, so it works without the environment; the
environment file exists so you can point at a different host without editing the collection.

## Start the system

```bash
docker compose up -d
```

```bash
./mvnw spring-boot:run
```

That is all the setup there is. **No SQL step and no manual sign-in** — folder `01` creates every id
the later folders depend on through the API itself, and a collection-level pre-request script
fetches a Keycloak token and reuses it until shortly before it expires.

The collection signs in as `admin.lms`, the only seeded user whose role satisfies every rule it
exercises. Change `adminUsername` if you want to watch a weaker role get refused; folder `07`
already does that deliberately.

Folder `03` creates the student record for the **Keycloak** `student` account rather than a locally
created user, so that account can actually log in — which is the only way folder `07` can
demonstrate owner-scoping, since that needs a caller who genuinely owns a record. Because that
identity is fixed rather than randomised, its record can only be created once per database, so the
step accepts `409` as well as `201` and reads the id back from `/students/me`.

`bootstrap-reference-data.sql` is still here as an *optional* shortcut: it inserts the same
reference data with **fixed** ids matching the environment file, which is convenient when you want
stable ids across runs or want to jump straight to the enrolment folders. It is not a prerequisite.

## Running it

Folders are numbered and must run in order — each one sets variables the next depends on:

| Folder | What it does | Sets |
|---|---|---|
| `00 · Health` | confirms the app is up and the schema validated | — |
| `01 · Reference data (run first)` | lecturer user → activate → grant role; faculty → department → programme; year → term → **open registration**; then reads each back | `lecturerUserId`, `facultyId`, `departmentId`, `programmeId`, `academicYearId`, `termId` |
| `02 · Courses` | create → **activate** → add section → **open section** | `courseId`, `courseCode`, `sectionId` |
| `03 · Students` | create the user, activate it, then create the student record | `studentUserId`, `studentId`, `studentNumber` |
| `04 · Enrollments` | enrol, prove the duplicate is refused, verify the seat count, drop | `enrollmentId` |
| `05 · Error contract` | nine deliberate failures | — |
| `06 · Section lifecycle (run last)` | closes the section and suspends the student — kept last so neither can break a full run | — |
| `07 · Authentication & authorization` | no token, a junk token, insufficient roles — then owner-scoping: a student refused another student's record, number lookup, listing, and enrolment | `otherStudentId` |

Four ordering constraints are real domain rules, not incidental:

- A user is created **PENDING_ACTIVATION** and must be **ACTIVE** before a student record can
  reference it.
- A term's registration window must be **open** before anyone can enrol.
- A course is created **DRAFT** and must be **ACTIVE** before a section can be added
  (`422 COURSE_NOT_OFFERABLE` otherwise).
- A section is created **PLANNED** and must be **OPEN** before anyone can enrol
  (`422 ENROLLMENT_SECTION_NOT_OPEN` otherwise).

Codes, usernames, course codes and student numbers are randomised by pre-request scripts, so the
collection is re-runnable without tripping the unique indexes.

## Running it headless

```bash
npx --yes newman@6 run docs/postman/University-LMS.postman_collection.json --env-var baseUrl=http://localhost:8080
```

Against an empty database this is 86 requests and 161 assertions, all green — and it is idempotent, so re-running against the same database stays green. Keycloak must be
running — the pre-request script fails loudly with a pointer to `docker compose up -d` rather than
letting the run collapse into a wall of unexplained 401s.

## What the tests assert

Every request has assertions. Beyond status codes:

- **Credentials never leak.** `01` asserts a created user carries no `password`, `passwordHash` or
  `credentials` field, and `05` posts a too-short password and asserts the rejected value comes back
  redacted rather than echoed.
- **The error envelope is complete** on *every* failure — `timestamp`, `status`, `code`, `message`,
  `path`, `traceId`.
- **Nothing internal leaks** — each error body is scanned for `sqlexception`, `org.hibernate`,
  `org.springframework`, `select `, `insert into` and `constraint `.
- **Listings are the transport page shape**, not Spring's `Page`, which would leak
  `pageable`/`sort` internals into a public contract.
- **Codes are normalised** — a faculty created as lower case comes back upper case.
- **`?search=%` is literal**, not a wildcard. This is a regression test: LIKE metacharacters were
  once unescaped, and the unfiltered listing itself once returned 500
  (see `docs/ROADMAP.md`, *Defects found by running the integration tests*).
- **Authorization actually refuses.** Folder `07` asserts 401 without a token, 403 for a valid
  token with the wrong role, and — the point of a denial suite that could otherwise pass by
  refusing everything — 200 for a student legitimately browsing the catalog and reading their own
  record.
- **Ownership is enforced for writes, not just reads.** A student is refused another student's
  record *and* refused enrolling them, which was the sharper of the two gaps.
- **`/students/me` is not parsed as a UUID.** Literal paths beat templates in Spring's matcher;
  the assertion pins it, because the failure mode is a confusing 400.
- **A rejected token discloses no reason.** The 401 body must not contain `malformed`,
  `signature`, `expired`, `audience` or `decode`, and `WWW-Authenticate` must carry no
  `error` parameter. Regression test: Spring's default entry point leaks all of that, and
  answers with an empty body besides.

## Correlating a request with the logs

Send `X-Correlation-Id` on any request and it is echoed back, appears as `traceId` in an error body,
and tags every server log line for that request:

```bash
curl -s -H 'X-Correlation-Id: my-trace-1' http://localhost:8080/api/v1/students/not-a-uuid
```

Omit the header and the server mints one.

## A note on the `postman/` subdirectory

`postman/` is a workspace mirror written by the Postman CLI against an **earlier** version of this
collection — its folder numbering (`01 · Courses`, `02 · Students`) predates the reference-data
folder. Treat `University-LMS.postman_collection.json` as the single source of truth; the mirror is
safe to delete or regenerate.

## Authentication

Bearer auth is set at collection level, so every request inherits it. The variables that drive it:

| Variable | Default |
|---|---|
| `keycloakUrl` | `http://localhost:8081` |
| `keycloakRealm` | `university-lms` |
| `keycloakClientId` | `university-lms-dev` |
| `adminUsername` / `adminPassword` | `admin.lms` / `password` |

The token is cached in `accessToken` with its expiry in `accessTokenExpiresAt`, and refetched 30
seconds before it lapses — so a long Collection Runner session does not fail halfway through.

Individual requests in folder `07` override this with `noauth`, a junk token, or a token fetched
for a weaker role in their own pre-request script.
