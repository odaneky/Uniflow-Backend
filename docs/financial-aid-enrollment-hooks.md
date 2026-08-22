# Financial aid and registration hold hooks

## Enrolment blocking

`EnrollmentService.requireNoRegistrationHolds(studentId, termId)` runs during:

- `POST /api/v1/enrollments` — single-section add (`enrol`)
- `POST /api/v1/enrollments/checkout` — cart checkout

It combines:

1. **Payment-plan / bursar hold** — existing `StudentBilling.standingOf(...).hold()`
2. **Service holds** — active rows in `service_holds` (FINANCIAL, SAP, ADVISING, ORIENTATION, PLACEMENT, MANUAL)
3. **SAP evaluation failure** — latest `sap_evaluations.meets_sap = false` for the student

Published contract: `com.university.lms.financialaid.api.RegistrationHolds` (implemented by `RegistrationHoldService`).

## SAP evaluation after grades

Call `SapService.evaluateAfterGrades(studentId, academicTermId)` when a term’s overall grades are final.

Suggested wiring (not yet automated):

- After `GradeService.publish` for an overall (non-assessment) grade, or
- From a batch job / outbox handler on `GRADE_PUBLISHED` once all sections for the term are closed

When SAP fails, the service places a `SAP` service hold and enrolment is blocked on the next registration attempt.

## Financial aid disbursement

`FinancialAidService.disburse(awardId)` posts a `CREDIT` entry to the student ledger (reference `fa-disburse:{awardId}`), which reduces the amount owed and may clear payment-plan holds via existing bursar standing logic.
