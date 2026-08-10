-- V5__student_schema.sql declared entrance_score as DECIMAL(6,2), but Student.entranceScore
-- is a Java Double (deliberately - it's a score, not currency, per ai-context/load-context.md's
-- Fees-vs-Marks precedent). Hibernate maps Double to SQL DOUBLE/FLOAT, not DECIMAL, so schema
-- validation on boot has always failed against a real (non-H2-test) database. V5 is already
-- applied and its checksum can't be edited in place - this corrects it forward instead.
ALTER TABLE students MODIFY COLUMN entrance_score DOUBLE;
