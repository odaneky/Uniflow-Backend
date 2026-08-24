-- A5 groundwork. Nothing narrows on org-scoped authorization yet — isStaff() still gates every
-- guard exactly as before — but isAppointedOver needs real data to consult before any guard can
-- safely switch to it. This is that data's anchor: one institution-wide org unit. Application code
-- (StaffingService.reconcileAppointments, and the automatic appointment made on every future role
-- grant) appoints every current and future staff-role holder here by default, which preserves
-- today's "any staff role reaches everywhere" behaviour exactly, since an appointment at the root
-- covers every descendant unit StaffAppointments.isAppointedOver walks.
INSERT INTO org_units (id, parent_org_unit_id, code, name, unit_type, created_at, updated_at)
VALUES (gen_random_uuid(), NULL, 'UNIV', 'Institution', 'INSTITUTION', now(), now());
