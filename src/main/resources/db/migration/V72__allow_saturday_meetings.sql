-- Evening and continuing-education sections meet on Saturdays; the original constraint made that
-- unstorable. day_of_week: 1 = Monday … 6 = Saturday (ISO, Sunday excluded).
ALTER TABLE section_meetings DROP CONSTRAINT ck_section_meetings_day;
ALTER TABLE section_meetings ADD CONSTRAINT ck_section_meetings_day CHECK (day_of_week BETWEEN 1 AND 6);
