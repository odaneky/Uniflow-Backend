# API Guidelines

Conventions every endpoint in `/api/v1` follows.

## Versioning

Base path `/api/v1`. A breaking change means a new version, not a mutation of the existing one.
Additive changes (new optional field, new endpoint) are made in place.

## Resources and methods

| Method | Use | Success |
|---|---|---|
| `GET` | read; safe and idempotent | `200` |
| `POST` | create, or a named action | `201` + `Location` for create, `200` for an action |
| `PATCH` | partial update — omitted fields mean *unchanged* | `200` |
| `PUT` | full replacement | `200` |
| `DELETE` | remove | `204` |

Every endpoint below requires a bearer token issued by Keycloak; `/actuator/health` and
`/actuator/info` are the only public paths. A missing or rejected token is `401`
`AUTHENTICATION_REQUIRED`, a valid token without sufficient authority is `403` `ACCESS_DENIED`, and
both arrive in the standard envelope like every other error. Neither says why: distinguishing
"expired" from "wrong audience" would hand an unauthenticated caller an oracle. The reason is in
the log against the same `traceId`.

Authorization has two layers, and they fail differently. A **role** rule refuses in the filter
chain, before any business code runs. An **ownership** rule refuses in the service layer, once the
record has been loaded — so a caller who owns nothing gets `403 ACCESS_DENIED`, never a `404` that
would let them enumerate which ids are real.

### Applicants

An applicant has no university identity — that is what they are applying for — so the admissions
endpoints are reachable without a bearer token. Access to a single application is granted by a
**capability token** issued when it is created and sent in `X-Application-Token` on every later call.

It is a capability, not an authentication credential: it names no one, grants access to exactly one
application, and expires. It travels in a header rather than the URL because a path parameter is
treated as non-secret by the whole stack — browser history, `Referer`, access and CDN logs, and
anything the applicant pastes into a support ticket.

| Endpoint | Access |
|---|---|
| `POST /api/v1/applications` | open; returns the token **once** |
| `POST /api/v1/applications/resume` | open; emails a fresh link. Always `202`, matched or not — any other answer would reveal whether a given person applied |
| `GET|PATCH /api/v1/applications/{id}`, `/submit`, `/documents` | the capability token, **or** signed-in admissions staff |

Refusals are always `403 APPLICATION_ACCESS_DENIED`, identical whether the token was absent, wrong,
expired, or the application does not exist — otherwise the response becomes an oracle for
discovering which ids are real. Resuming rotates the token, so it doubles as a way to revoke a link
the applicant thinks has leaked.

Endpoints are rate limited per client address, **before** authentication — a flood aimed at a public
endpoint is turned away before token parsing or a database connection is involved. Over the limit is
`429 RATE_LIMIT_EXCEEDED` with a `Retry-After` header, in the same envelope as every other error. The
body names neither the rule nor the remaining allowance, so a caller cannot map the thresholds and
pace themselves just underneath.

Limits are configured under `lms.rate-limit`, evaluated first-match-wins like the authorization
rules. The unauthenticated admissions endpoints are the tightest (10 submissions per hour per
address); `/actuator` is never limited, because throttling a liveness probe turns a traffic spike
into a restart. Per-user limits on authenticated actions are a separate layer that lives with the
services owning them — the two use different keys because they answer different questions: "is this
address abusing us" versus "is this person abusing us".

**Behind a load balancer, set `lms.rate-limit.trusted-proxies`** to the balancer's CIDR range.
`X-Forwarded-For` is ignored by default; without that setting every user shares one bucket, and with
it set too widely a caller can forge the header and bypass the limits entirely.

Current endpoints — 45 across ten controllers:

```
GET    /api/v1/me
GET    /api/v1/students/me

POST   /api/v1/users
GET    /api/v1/users?page=&size=&sort=
GET    /api/v1/users/{id}
POST   /api/v1/users/{id}/activate
POST   /api/v1/users/{id}/suspend
GET    /api/v1/users/{id}/roles
POST   /api/v1/users/{id}/roles?role=

POST   /api/v1/faculties
GET    /api/v1/faculties?page=&size=&sort=
GET    /api/v1/faculties/{id}

POST   /api/v1/departments
GET    /api/v1/departments?facultyId=&page=&size=&sort=
GET    /api/v1/departments/{id}

POST   /api/v1/programmes
GET    /api/v1/programmes?departmentId=&page=&size=&sort=
GET    /api/v1/programmes/{id}

POST   /api/v1/academic-years
GET    /api/v1/academic-years?page=&size=&sort=
GET    /api/v1/academic-years/{id}
GET    /api/v1/academic-years/{id}/terms

POST   /api/v1/academic-terms
GET    /api/v1/academic-terms/{id}
PUT    /api/v1/academic-terms/{id}/registration-window

POST   /api/v1/students
GET    /api/v1/students?status=&programmeId=&page=&size=&sort=
GET    /api/v1/students/{id}
GET    /api/v1/students/by-number/{studentNumber}
PATCH  /api/v1/students/{id}

POST   /api/v1/courses
GET    /api/v1/courses?status=&departmentId=&search=&page=&size=&sort=
GET    /api/v1/courses/{id}
GET    /api/v1/courses/by-code/{courseCode}
PATCH  /api/v1/courses/{id}
POST   /api/v1/courses/{id}/sections
GET    /api/v1/courses/{id}/sections
POST   /api/v1/courses/sections/{sectionId}/open
POST   /api/v1/courses/sections/{sectionId}/close

POST   /api/v1/enrollments
GET    /api/v1/enrollments?studentId=&courseSectionId=&status=&page=&size=&sort=
GET    /api/v1/enrollments/{id}
POST   /api/v1/enrollments/{id}/drop
POST   /api/v1/enrollments/{id}/withdraw
POST   /api/v1/enrollments/{id}/complete
```

Three of these are state transitions expressed as `POST /{id}/{verb}` rather than a `PATCH` of a
status field: `activate`, `suspend`, `open`, `close`, `drop`, `withdraw`, `complete`. Each is a
transition the domain either permits or refuses — the service consults an explicit state machine
and returns `422` with a stated reason when it refuses — so modelling them as a settable field
would invite callers to assume any value is assignable.

`PUT /api/v1/academic-terms/{id}/registration-window` is a `PUT` because it is idempotent: the
window is replaced wholesale, and both ends must be supplied together. Opening registration is what
lets students begin competing for seats, so it is its own individually auditable action rather than
a field on a general term update.

### Why lifecycle changes are actions, not `PATCH {status}`

`POST /enrollments/{id}/drop` rather than `PATCH /enrollments/{id}` with `{"status":"DROPPED"}`.
Dropping and withdrawing differ in academic consequence, and a status field invites clients to
attempt transitions the domain forbids. Named actions make the permitted operations explicit and
keep the state machine authoritative.

## Status codes

| Code | Meaning here |
|---|---|
| `200` | success |
| `201` | created; `Location` header points at the new resource |
| `204` | success, no body |
| `400` | malformed or invalid request |
| `401` | not authenticated |
| `403` | authenticated but not permitted |
| `404` | resource does not exist (or is not visible to the caller) |
| `409` | conflicts with current state: duplicate, section full, version conflict |
| `422` | well-formed but violates a business rule |
| `500` | unexpected; opaque body plus a `traceId` |

`404` is returned rather than `403` where revealing existence would itself leak information.

## The error contract

Every failure — validation, business rule, security, or unexpected — returns this shape:

```json
{
  "timestamp": "2026-08-19T18:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/students",
  "traceId": "7b4c1f9e-2a10-4c33-9d51-0f2a7c8b1d44",
  "errors": [
    { "field": "email", "message": "must be a valid email address", "rejectedValue": "not-an-email" }
  ]
}
```

**Branch on `code`, never on `message`.** Codes are stable identifiers; messages are prose and may
be reworded at any time. `errors` is present only for field-level failures and is omitted otherwise.

`traceId` matches the `X-Correlation-Id` response header and the `traceId` in every server log line
for that request — quote it in a bug report and an operator can find the exact failure.

### Codes

Cross-cutting (`CommonErrorCode`): `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `CONSTRAINT_VIOLATION`,
`RESOURCE_NOT_FOUND`, `RESOURCE_ALREADY_EXISTS`, `DATA_INTEGRITY_VIOLATION`,
`CONCURRENT_MODIFICATION`, `ACCESS_DENIED`, `AUTHENTICATION_REQUIRED`, `METHOD_NOT_ALLOWED`,
`UNSUPPORTED_MEDIA_TYPE`, `INTERNAL_ERROR`.

Module-owned: `STUDENT_NOT_FOUND`, `STUDENT_NUMBER_ALREADY_EXISTS`, `COURSE_NOT_FOUND`,
`COURSE_CODE_ALREADY_EXISTS`, `ENROLLMENT_ALREADY_EXISTS`, `ENROLLMENT_SECTION_FULL`,
`INVALID_ENROLLMENT_STATE`, … Each module defines its own enum implementing `ErrorCode`, so error
vocabulary stays with the module rather than accumulating centrally.

### What never appears in an error body

Stack traces, SQL, constraint names, class names, credentials, tokens. All of it is logged against
the `traceId` instead. Rejected values are echoed back because that makes an error actionable — but
fields whose name looks secret-shaped (`password`, `token`, `secret`, `apiKey`, …) have their value
suppressed by `SensitiveDataMasker`.

## Pagination

Any collection that can grow is paged. There is no unpaged list endpoint.

```
GET /api/v1/students?page=0&size=20&sort=studentNumber,asc
```

- `page` is zero-based; default size `20`; **maximum size `100`**, enforced globally so a client
  cannot request the entire table.
- The response is a `PageResponse`, deliberately not Spring's `Page` — that type serialises its
  internal `Pageable`/`Sort` structure, which would leak persistence detail into a contract we
  then could not change.

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 1843,
  "totalPages": 93,
  "first": true,
  "last": false
}
```

Sub-collections that are inherently small and bounded (a course's sections) return a plain list.

## Validation

Structural validation is declared on the request record and runs at the boundary:

```java
public record CreateCourseRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Z]{2,6}[0-9]{3,5}$") String courseCode,
        @NotNull @Positive @Max(60) Integer credits,
        ...) {}
```

Anything requiring live data — does this programme exist, is this student number taken — is
business validation and belongs in the service, because it cannot be decided from the payload alone.

## DTOs

JPA entities are never exposed. Requests and responses are immutable `record`s, separated by use
case:

- `CreateXRequest`, `UpdateXRequest`, `XResponse`, `XSummaryResponse`

`XSummaryResponse` exists so paging through thousands of rows does not serialise fields no list view
displays — `CourseSummaryResponse` omits the description, which otherwise dominates payload size.

## Correlation

Send `X-Correlation-Id` to propagate an existing trace; otherwise one is generated. It is always
echoed on the response.
