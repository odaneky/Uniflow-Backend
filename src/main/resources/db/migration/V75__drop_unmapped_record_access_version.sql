-- A7: V52 added a `version` column to record_access_events, but RecordAccessEvent never declares
-- @Version — BaseEntity deliberately omits it, and each entity opts in only where contention is
-- real (docs/concurrency.md). This table is an append-only log, never updated, so the column was
-- dead from the start: unmapped by Hibernate and misleading about a locking guarantee that isn't
-- actually enforced.
ALTER TABLE record_access_events DROP COLUMN version;
