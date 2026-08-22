# Service request workflow

Type-routed registry requests with enforced transitions, outbox notifications, and domain fulfilment.

## Status flow

```
SUBMITTED → IN_REVIEW → APPROVED → COMPLETED
              ↘ DENIED
```

Students may cancel while `SUBMITTED` (→ `DENIED`).

## Types and routing

| Type | Assignee | Payload | Fulfilment on COMPLETED |
|------|----------|---------|-------------------------|
| WITHDRAWAL | Student's advisor | `enrollmentId`, `reason` | `EnrollmentActions.withdraw` |
| APPEAL | Section lecturer / registrar | `gradeId`, `reason` | `GradeAppealActions.resolveAppeal` (after APPROVE opens appeal) |
| TRANSCRIPT | Registrar pool | `deliveryMethod` | Requires `deliverableDocumentId` |
| VERIFICATION | Registrar pool | `purpose` | Requires `deliverableDocumentId` |
| GRADUATION | Registrar | optional `expectedGraduationTermId` | `DegreeAudit` + `StudentLifecycle.graduate` |

## API

**Student:** `GET/POST /api/v1/me/requests`, `POST .../{id}/cancel`

**Staff:** `GET /api/v1/requests`, `GET .../{id}`, `POST .../{id}/claim|review|approve|deny|complete`

Legacy `POST .../decide` maps to the explicit transitions.

## Notifications

Outbox events: `ServiceRequestSubmitted`, `ServiceRequestStatusChanged`, `ServiceRequestDelivered` → `NotificationType.SERVICE_REQUEST` with deep links.

## Migrations

- V47 — payload, events, assignee, deliverable, partial unique index
- V48 — `grades.under_appeal`
