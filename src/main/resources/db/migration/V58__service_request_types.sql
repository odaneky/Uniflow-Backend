-- Extend service request types for community-college pilot workflows.

ALTER TABLE service_requests DROP CONSTRAINT IF EXISTS ck_service_requests_type;

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
            'READMISSION'
        )
    );
