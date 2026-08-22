-- Reference data the application assumes exists at startup.
--
-- Ids are literal rather than generated so that this migration is reproducible across
-- environments and so a later migration can reference a role without a lookup.

INSERT INTO roles (id, name, description, created_at, updated_at, created_by, updated_by) VALUES
    ('11111111-0000-4000-8000-000000000001', 'STUDENT',          'Enrolled student',                       now(), now(), 'flyway', 'flyway'),
    ('11111111-0000-4000-8000-000000000002', 'LECTURER',         'Teaches course sections',                now(), now(), 'flyway', 'flyway'),
    ('11111111-0000-4000-8000-000000000003', 'ACADEMIC_ADVISOR', 'Advises students on programme progress', now(), now(), 'flyway', 'flyway'),
    ('11111111-0000-4000-8000-000000000004', 'FACULTY_ADMIN',    'Administers a faculty',                  now(), now(), 'flyway', 'flyway'),
    ('11111111-0000-4000-8000-000000000005', 'REGISTRAR',        'Owns student records and registration',  now(), now(), 'flyway', 'flyway'),
    ('11111111-0000-4000-8000-000000000006', 'SYSTEM_ADMIN',     'Full administrative access',             now(), now(), 'flyway', 'flyway');

INSERT INTO permissions (id, name, description, created_at, updated_at, created_by, updated_by) VALUES
    ('22222222-0000-4000-8000-000000000001', 'COURSE_READ',      'View the course catalog',        now(), now(), 'flyway', 'flyway'),
    ('22222222-0000-4000-8000-000000000002', 'COURSE_WRITE',     'Create and amend courses',       now(), now(), 'flyway', 'flyway'),
    ('22222222-0000-4000-8000-000000000003', 'STUDENT_READ',     'View student records',           now(), now(), 'flyway', 'flyway'),
    ('22222222-0000-4000-8000-000000000004', 'STUDENT_WRITE',    'Create and amend student records', now(), now(), 'flyway', 'flyway'),
    ('22222222-0000-4000-8000-000000000005', 'ENROLLMENT_WRITE', 'Register students into sections', now(), now(), 'flyway', 'flyway'),
    ('22222222-0000-4000-8000-000000000006', 'GRADE_WRITE',      'Award and amend grades',          now(), now(), 'flyway', 'flyway');

-- Registrar: the operational owner of records and registration.
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('11111111-0000-4000-8000-000000000005', '22222222-0000-4000-8000-000000000003'),
    ('11111111-0000-4000-8000-000000000005', '22222222-0000-4000-8000-000000000004'),
    ('11111111-0000-4000-8000-000000000005', '22222222-0000-4000-8000-000000000005'),
    ('11111111-0000-4000-8000-000000000005', '22222222-0000-4000-8000-000000000001'),
    -- Lecturer: reads the catalog, awards grades.
    ('11111111-0000-4000-8000-000000000002', '22222222-0000-4000-8000-000000000001'),
    ('11111111-0000-4000-8000-000000000002', '22222222-0000-4000-8000-000000000006'),
    -- Student: catalog visibility only.
    ('11111111-0000-4000-8000-000000000001', '22222222-0000-4000-8000-000000000001');

-- A default marking scheme so grading is usable out of the box. Bands are contiguous and cover
-- 0-100 with no gap, which the awarding logic relies on to always find exactly one band.
INSERT INTO grade_scales (id, name, description, active, created_at, updated_at, created_by, updated_by) VALUES
    ('33333333-0000-4000-8000-000000000001', 'Undergraduate Standard', 'Default undergraduate marking scheme', true, now(), now(), 'flyway', 'flyway');

INSERT INTO grade_scale_bands (id, grade_scale_id, letter, min_percent, max_percent, grade_point, created_at, updated_at, created_by, updated_by) VALUES
    ('44444444-0000-4000-8000-000000000001', '33333333-0000-4000-8000-000000000001', 'A+', 90.00, 100.00, 4.00, now(), now(), 'flyway', 'flyway'),
    ('44444444-0000-4000-8000-000000000002', '33333333-0000-4000-8000-000000000001', 'A',  80.00, 89.99,  3.70, now(), now(), 'flyway', 'flyway'),
    ('44444444-0000-4000-8000-000000000003', '33333333-0000-4000-8000-000000000001', 'B',  70.00, 79.99,  3.00, now(), now(), 'flyway', 'flyway'),
    ('44444444-0000-4000-8000-000000000004', '33333333-0000-4000-8000-000000000001', 'C',  60.00, 69.99,  2.00, now(), now(), 'flyway', 'flyway'),
    ('44444444-0000-4000-8000-000000000005', '33333333-0000-4000-8000-000000000001', 'D',  50.00, 59.99,  1.00, now(), now(), 'flyway', 'flyway'),
    ('44444444-0000-4000-8000-000000000006', '33333333-0000-4000-8000-000000000001', 'F',   0.00, 49.99,  0.00, now(), now(), 'flyway', 'flyway');
