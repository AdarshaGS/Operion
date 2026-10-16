-- Classes become their own School Starter Pack list (template category CLASS): one row per grade, named after the
-- grade. Apply only creates a class (and its sections) when both its CLASS row and the matching GRADE row are ticked,
-- so leaving a grade out leaves its class out too.

INSERT INTO school_template_master_items (category, name, sequence_order, stage, module_code, created_at, updated_at)
SELECT 'CLASS', name, sequence_order, stage, 'ACADEMICS', NOW(6), NOW(6)
FROM school_template_master_items
WHERE category = 'GRADE';

-- Organisations whose template catalog was already copied from the master list will not be re-seeded. Give each one a
-- CLASS row for every grade row it has (custom grades included), ticked exactly when its grade is, so nothing it had
-- already chosen changes. Organisations with no catalog yet get the master rows on first read.
INSERT IGNORE INTO school_template_items
  (organisation_id, category, name, sequence_order, stage, module_code, included, created_at, updated_at)
SELECT organisation_id, 'CLASS', name, sequence_order, stage, 'ACADEMICS', included, NOW(6), NOW(6)
FROM school_template_items
WHERE category = 'GRADE';
