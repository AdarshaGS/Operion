-- Default subjects for the School Starter Pack (template category SUBJECT): a generic core set a school can
-- untick, rename or extend. Subjects are catalog rows only - which class teaches which subject (and whether it
-- is mandatory there) is still set per class in Academics.

INSERT INTO school_template_master_items (category, name, code, module_code, created_at, updated_at)
VALUES
('SUBJECT', 'English', 'ENG', 'ACADEMICS', NOW(6), NOW(6)),
('SUBJECT', 'Mathematics', 'MATH', 'ACADEMICS', NOW(6), NOW(6)),
('SUBJECT', 'Science', 'SCI', 'ACADEMICS', NOW(6), NOW(6)),
('SUBJECT', 'Social Science', 'SOC', 'ACADEMICS', NOW(6), NOW(6)),
('SUBJECT', 'Second Language', 'LANG2', 'ACADEMICS', NOW(6), NOW(6)),
('SUBJECT', 'Computer Science', 'CS', 'ACADEMICS', NOW(6), NOW(6)),
('SUBJECT', 'Physical Education', 'PE', 'ACADEMICS', NOW(6), NOW(6)),
('SUBJECT', 'Art', 'ART', 'ACADEMICS', NOW(6), NOW(6));

-- Organisations whose template catalog was already copied from the master list will not be re-seeded, so give
-- them the new rows now (organisations with no catalog yet get them on first read). IGNORE skips a row an admin
-- already added by hand under the same (organisation_id, category, name).
INSERT IGNORE INTO school_template_items (organisation_id, category, name, code, module_code, created_at, updated_at)
SELECT o.organisation_id, m.category, m.name, m.code, m.module_code, NOW(6), NOW(6)
FROM (SELECT DISTINCT organisation_id FROM school_template_items) o
JOIN school_template_master_items m ON m.category = 'SUBJECT';
