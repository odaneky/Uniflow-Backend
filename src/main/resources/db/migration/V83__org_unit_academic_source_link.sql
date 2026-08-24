-- A5 groundwork continued. Faculty/Department (academic) and OrgUnit (staffing) are deliberately
-- separate tables (see V71) — this links them without either module writing into the other's
-- tables: org_units carries a reference back to the academic entity it mirrors, rather than
-- academic.faculties/departments carrying a reference forward. Populated by
-- StaffingService.ensureOrgUnitFor, reacting to an outbox event AcademicStructureService publishes
-- whenever a faculty or department is created (and, for existing ones, by a registry-triggered
-- reconcile pass) — never written directly.
ALTER TABLE org_units
    ADD COLUMN source_type VARCHAR(20),
    ADD COLUMN source_id   UUID;

CREATE UNIQUE INDEX uk_org_units_source ON org_units (source_type, source_id)
    WHERE source_type IS NOT NULL AND source_id IS NOT NULL;
