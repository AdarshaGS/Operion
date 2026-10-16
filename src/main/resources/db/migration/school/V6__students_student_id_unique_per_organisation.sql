-- #354: every organisation's per-year student-id counter restarts at 1, so the globally unique key made a second
-- organisation's first student of a year collide with the first organisation's (STU-2026-00001). Scope it per organisation.
ALTER TABLE students DROP INDEX uq_students_student_id;
ALTER TABLE students ADD CONSTRAINT uq_students_org_student_id UNIQUE (organisation_id, student_id);
