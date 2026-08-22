-- Allow student-cancelled requests as a distinct terminal status.

ALTER TABLE service_requests DROP CONSTRAINT IF EXISTS ck_service_requests_status;
ALTER TABLE service_requests ADD CONSTRAINT ck_service_requests_status CHECK (
    status IN ('SUBMITTED', 'IN_REVIEW', 'APPROVED', 'COMPLETED', 'DENIED', 'CANCELLED')
);

DROP INDEX IF EXISTS uk_service_requests_open_per_type;
CREATE UNIQUE INDEX uk_service_requests_open_per_type
    ON service_requests (student_id, request_type)
    WHERE status NOT IN ('COMPLETED', 'DENIED', 'CANCELLED');
