-- G4: attendance_records (V9) was superseded by attendance_sessions + attendance_marks (V57) but
-- never removed. Nothing in the application reads or writes it — AttendanceService and
-- AttendanceController never reference AttendanceRecord, only its own now-deleted repository did.
DROP TABLE attendance_records;
