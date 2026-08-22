# UniFlow LMS — Java Backend

A production-grade **modular monolith** backend for the university platform. It is independent of
the React prototype in the repository root; the two share only this repository.

## Stack

Java 21 · Spring Boot 3.3 · Spring Data JPA · Flyway · PostgreSQL 16 · Spring Security ·
Micrometer + OpenTelemetry · JUnit 5 · Mockito · Testcontainers

## Run it

All commands run from this directory.

```bash
docker compose up -d          # PostgreSQL on :5432, Keycloak on :8081
docker compose --profile obs up -d   # optional: Grafana LGTM + OTel Collector
```

```bash
./mvnw spring-boot:run
```

The API is then on `http://localhost:8080/api/v1`. Keycloak's admin console is on
`http://localhost:8081` (`admin` / `admin`), realm `university-lms`.

With the `local` profile, Actuator (health, Prometheus) binds to loopback
`http://127.0.0.1:8082` so scrape never rides on the public API port. Elsewhere, health stays on
`/actuator/health` of the API connector. Production exports OTLP only
(`OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` and `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT`).

### Grafana (optional)

```bash
docker compose --profile obs up -d
```

Then open [http://localhost:3000](http://localhost:3000) (`admin` / `admin` — throwaway, like Keycloak).
The app sends traces, metrics and logs to the collector on `localhost:4318`. The **UniFlow overview**
dashboard is provisioned automatically. This is the ops UI; it is not a UniFlow screen.

**Every `/api/v1` call needs a bearer token.** Get one, and call something:

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/university-lms/protocol/openid-connect/token -d grant_type=password -d client_id=university-lms-dev -d username=admin.lms -d password=password | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
```

```bash
curl -s -H "Authorization: Bearer $TOKEN" 'localhost:8080/api/v1/courses?page=0&size=20'
```

Seeded users, all with password `password`: `admin.lms` (SYSTEM_ADMIN), `registrar`, `lecturer`,
`advisor`, `student`. See [docker/keycloak/README.md](docker/keycloak/README.md).

Holding only a token, the first call to make is `/api/v1/me` — it resolves the token to a user id
and provisions the local row if this is the subject's first ever request:

```bash
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/me
```

```bash
./mvnw test        # full test suite
./mvnw package     # executable jar
```

Integration tests need PostgreSQL. Without one they **skip** rather than fail, so a red build always
means a real defect — but check the skip count, because a silently skipped suite proves nothing.

If Testcontainers cannot reach your Docker daemon (it cannot negotiate with Docker Engine 29), run
them against a database you already have:

```bash
docker compose exec -T postgres psql -U lms -d postgres -c 'CREATE DATABASE university_lms_test;'
./mvnw verify -Dlms.test.datasource.url=jdbc:postgresql://localhost:5432/university_lms_test
```

Point that at a dedicated database — Flyway migrates whatever it is given.

## First requests

A course needs a department, and a department needs a faculty, so the chain starts there. Each call
carries the `$TOKEN` from above:

```bash
FACULTY=$(curl -sX POST localhost:8080/api/v1/faculties -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"code":"FST","name":"Science and Technology"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')

DEPT=$(curl -sX POST localhost:8080/api/v1/departments -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"facultyId\":\"$FACULTY\",\"code\":\"COMP\",\"name\":\"Computing\"}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')

curl -sX POST localhost:8080/api/v1/courses -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"courseCode\":\"COMP3101\",\"title\":\"Software Engineering\",\"credits\":3,
       \"level\":3,\"departmentId\":\"$DEPT\",\"components\":[\"LECTURE\",\"TUTORIAL\"]}"
```

```bash
curl -s -H "Authorization: Bearer $TOKEN" 'localhost:8080/api/v1/courses?page=0&size=20&sort=courseCode,asc'
```

Every listing endpoint is paged, and returns the transport page shape rather than Spring's `Page`.

## Layout

```
UniPro-Backend/
  pom.xml
  src/main/java/com/university/lms/
    common/                 error model, auditing, security, pagination
    identity/ academic/ student/ course/ enrollment/
    learning/ assessment/ grading/ attendance/
    communication/ notification/ document/ administration/
  src/main/resources/db/migration/    Flyway migrations
  docker/                   application Dockerfile
    keycloak/realm/         imported realm: roles, clients, dev users
  docker-compose.yml        local PostgreSQL + Keycloak
  infrastructure/           reserved for IaC
  docs/                     architecture, modules, database, API, concurrency, roadmap, Postman
```

## Documentation

| Document | Covers |
|---|---|
| [glossary.md](docs/glossary.md) | course, occurrence/section, component, meeting, faculty vs lecturer |
| [architecture.md](docs/architecture.md) | why a modular monolith, module boundaries, layering, deliberate deviations |
| [modules.md](docs/modules.md) | ownership, dependency graph, implementation depth per module |
| [database.md](docs/database.md) | schema ownership, migrations, constraints, indexing, N+1 |
| [api-guidelines.md](docs/api-guidelines.md) | REST conventions, the error contract, pagination, validation |
| [concurrency.md](docs/concurrency.md) | the enrolment races and exactly how each is prevented |
| [**identity-architecture-assessment.md**](docs/identity-architecture-assessment.md) | **where the identity architecture stands against the target, and the migration sequence** |
| [**ROADMAP.md**](docs/ROADMAP.md) | **what is still missing, prioritised — read this before planning work** |
| [postman/](docs/postman/README.md) | importable API collection, 86 requests covering all 45 endpoints, signs itself in |
| [docker/keycloak/](docker/keycloak/README.md) | the Keycloak realm, how to get a token, how to re-import |
| [java-backend-architecture.md](docs/java-backend-architecture.md) | earlier design study written against the React prototype |

## Trying the API

```bash
docker compose up -d
```

```bash
./mvnw spring-boot:run
```

Import `docs/postman/University-LMS.postman_collection.json` and run it top to bottom — or headless:

```bash
npx --yes newman@6 run docs/postman/University-LMS.postman_collection.json --env-var baseUrl=http://localhost:8080
```

No database seeding is needed, and no manual sign-in: a collection-level pre-request script fetches
a token from Keycloak and reuses it until it expires. Against an **empty** database the collection
builds the whole structure through the API — lecturer account, faculty, department, programme,
academic year, term with registration open, course, section, student, enrolment — then proves the
security rules bite, in 86 requests and 161 assertions. It is idempotent: run it as many times as
you like against the same database.

## Build a container image

```bash
docker build -f docker/Dockerfile -t university-lms:local .
```

Multi-stage: the runtime image carries a JRE and one jar, not a JDK and the dependency cache.

## Two things to know before extending it

1. **A token is not an identity until it is resolved.** Keycloak's `sub` names nothing in this
   database; `users.keycloak_subject` is the join, and `CurrentUserProvider` walks it, provisioning
   a row on first sight. Without that link no endpoint could answer "my courses" and no check could
   ask "is this yours" — which is why authorization is role-based *and* owner-scoped, in that order:
   roles in `SecurityConfig`, ownership in the service layer where the entity is in hand.
2. **An application `if` is never the concurrency guarantee.** Uniqueness is enforced by database
   constraints and seat capacity by a single guarded `UPDATE`. See
   [concurrency.md](docs/concurrency.md) before touching enrolment.
