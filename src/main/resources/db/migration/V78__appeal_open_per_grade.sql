-- D3 (partial): uk_service_requests_open_per_type treats every open APPEAL as the same slot per
-- student, so a student appealing grades in two different courses at once hits it — a student
-- cannot appeal two grades at once. A grade appeal's payload already carries the specific gradeId
-- being contested (ServiceRequestPayloadValidator.validateAppeal), and a grade row belongs to
-- exactly one student, so uniqueness on gradeId alone is sufficient without needing student_id in
-- the key. Every other request type keeps the original one-open-per-type rule; the general D3 item
-- (per-type transition graphs) is a separate, larger piece of work, not attempted here.
DROP INDEX uk_service_requests_open_per_type;
CREATE UNIQUE INDEX uk_service_requests_open_per_type
    ON service_requests (student_id, request_type)
    WHERE status NOT IN ('COMPLETED', 'DENIED', 'CANCELLED') AND request_type <> 'APPEAL';

CREATE UNIQUE INDEX uk_service_requests_open_appeal_per_grade
    ON service_requests ((payload ->> 'gradeId'))
    WHERE request_type = 'APPEAL' AND status NOT IN ('COMPLETED', 'DENIED', 'CANCELLED');
