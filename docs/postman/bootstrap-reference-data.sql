-- Optional development seed data for the Postman collection.
--
-- NOT REQUIRED. The identity and academic modules now expose REST endpoints, so folder
-- "01 · Reference data" of the Postman collection creates all of this through the API. Keep using
-- this script only when you want *fixed* ids — they match the Postman environment file, which is
-- convenient if you want stable ids across runs, or want to skip straight to the enrolment folders.
--
-- This is NOT a Flyway migration and must never become one: it is developer convenience data,
-- not schema.
--
-- Run against a running local database:
--   docker compose exec -T postgres psql -U lms -d university_lms < docs/postman/bootstrap-reference-data.sql
--
-- Ids are fixed so they can be referenced from the Postman environment. Re-running is safe.

-- A user to attach a student record to, and to act as a lecturer.
INSERT INTO users (id, username, email, password_hash, first_name, last_name, status,
                   created_at, updated_at, created_by, updated_by)
VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'jdoe', 'jdoe@university.test',
        -- Placeholder only. Authentication is not implemented; no login uses this value.
        '$2a$10$placeholderplaceholderplaceholderplaceholderplaceholderpla',
        'John', 'Doe', 'ACTIVE', now(), now(), 'bootstrap', 'bootstrap')
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, username, email, password_hash, first_name, last_name, status,
                   created_at, updated_at, created_by, updated_by)
VALUES ('aaaaaaaa-0000-4000-8000-000000000002', 'mchen', 'mchen@university.test',
        '$2a$10$placeholderplaceholderplaceholderplaceholderplaceholderpla',
        'Michael', 'Chen', 'ACTIVE', now(), now(), 'bootstrap', 'bootstrap')
ON CONFLICT (id) DO NOTHING;

-- Faculty -> Department -> Programme
INSERT INTO faculties (id, code, name, created_at, updated_at, created_by, updated_by)
VALUES ('bbbbbbbb-0000-4000-8000-000000000001', 'FSCI', 'Faculty of Science',
        now(), now(), 'bootstrap', 'bootstrap')
ON CONFLICT (id) DO NOTHING;

INSERT INTO departments (id, faculty_id, code, name, created_at, updated_at, created_by, updated_by)
VALUES ('cccccccc-0000-4000-8000-000000000001', 'bbbbbbbb-0000-4000-8000-000000000001',
        'COMP', 'Department of Computing', now(), now(), 'bootstrap', 'bootstrap')
ON CONFLICT (id) DO NOTHING;

INSERT INTO programmes (id, department_id, code, name, degree_award, total_credits, duration_years,
                        active, created_at, updated_at, created_by, updated_by)
VALUES ('dddddddd-0000-4000-8000-000000000001', 'cccccccc-0000-4000-8000-000000000001',
        'BSCCS', 'BSc Computer Science', 'BSc (Hons)', 120, 3, true,
        now(), now(), 'bootstrap', 'bootstrap')
ON CONFLICT (id) DO NOTHING;

-- Academic year and a term whose registration window is OPEN RIGHT NOW.
-- Enrolment is refused outside this window, so the dates are relative to now() on purpose.
INSERT INTO academic_years (id, code, start_date, end_date, created_at, updated_at, created_by, updated_by)
VALUES ('eeeeeeee-0000-4000-8000-000000000001', '2026/2027',
        DATE '2026-09-01', DATE '2027-08-31', now(), now(), 'bootstrap', 'bootstrap')
ON CONFLICT (id) DO NOTHING;

INSERT INTO academic_terms (id, academic_year_id, name, term_type, sequence_number,
                            start_date, end_date, registration_opens_at, registration_closes_at,
                            created_at, updated_at, created_by, updated_by)
VALUES ('ffffffff-0000-4000-8000-000000000001', 'eeeeeeee-0000-4000-8000-000000000001',
        'Semester 1', 'SEMESTER', 1, DATE '2026-09-01', DATE '2026-12-20',
        now() - INTERVAL '1 day', now() + INTERVAL '90 days',
        now(), now(), 'bootstrap', 'bootstrap')
ON CONFLICT (id) DO NOTHING;

-- If the window has since elapsed, reopen it rather than re-running the whole file.
UPDATE academic_terms
   SET registration_opens_at  = now() - INTERVAL '1 day',
       registration_closes_at = now() + INTERVAL '90 days',
       updated_at             = now()
 WHERE id = 'ffffffff-0000-4000-8000-000000000001'
   AND registration_closes_at < now();

SELECT 'bootstrap complete' AS status,
       (SELECT count(*) FROM users)      AS users,
       (SELECT count(*) FROM programmes) AS programmes,
       (SELECT count(*) FROM academic_terms WHERE registration_closes_at > now()) AS open_terms;
