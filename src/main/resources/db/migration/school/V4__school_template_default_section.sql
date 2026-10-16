-- Default section for the School Starter Pack (template category SECTION): each class gets a section named "A".
-- It is a template row like any other, so a school can untick it, rename it, or add B, C, ... and every class
-- receives all the ticked ones.

INSERT INTO school_template_master_items (category, name, module_code, created_at, updated_at)
VALUES ('SECTION', 'A', 'ACADEMICS', NOW(6), NOW(6));

-- Organisations whose template catalog was already copied from the master list will not be re-seeded, so give
-- them the new row now (organisations with no catalog yet get it on first read). IGNORE skips a row an admin
-- already added by hand under the same (organisation_id, category, name).
INSERT IGNORE INTO school_template_items (organisation_id, category, name, module_code, created_at, updated_at)
SELECT o.organisation_id, m.category, m.name, m.module_code, NOW(6), NOW(6)
FROM (SELECT DISTINCT organisation_id FROM school_template_items) o
JOIN school_template_master_items m ON m.category = 'SECTION';
