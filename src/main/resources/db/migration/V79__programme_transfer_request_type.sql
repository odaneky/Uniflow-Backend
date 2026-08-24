-- D5: a programme transfer previously had no request/review path — the only way to change a
-- student's programme was a registrar's direct PATCH /students/{id}, unreviewed and unrecorded as
-- a distinct decision. Adds PROGRAMME_TRANSFER as a request type students can submit and registry
-- can approve or deny; the direct PATCH path is left in place as a registrar override rather than
-- removed, since replacing staff's existing administrative capability is a separate decision.
ALTER TABLE service_requests DROP CONSTRAINT ck_service_requests_type;

ALTER TABLE service_requests
    ADD CONSTRAINT ck_service_requests_type CHECK (
        request_type IN (
            'TRANSCRIPT',
            'WITHDRAWAL',
            'VERIFICATION',
            'APPEAL',
            'GRADUATION',
            'PROFILE_CORRECTION',
            'SAP_APPEAL',
            'LATE_ADD',
            'COURSE_SUBSTITUTION',
            'LEAVE_OF_ABSENCE',
            'READMISSION',
            'PROGRAMME_TRANSFER'
        )
    );
