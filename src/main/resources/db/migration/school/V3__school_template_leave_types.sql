-- Default HR leave types for the School Starter Pack (template category LEAVE_TYPE). Names and codes only:
-- how many days each type grants is school policy, so default_annual_days stays blank until an admin sets it.

INSERT INTO school_template_master_items (category, name, code, module_code, created_at, updated_at)
VALUES
('LEAVE_TYPE', 'Casual Leave', 'CASUAL', 'HR', NOW(6), NOW(6)),
('LEAVE_TYPE', 'Sick Leave', 'SICK', 'HR', NOW(6), NOW(6)),
('LEAVE_TYPE', 'Earned Leave', 'EARNED', 'HR', NOW(6), NOW(6)),
('LEAVE_TYPE', 'Maternity Leave', 'MATERNITY', 'HR', NOW(6), NOW(6)),
('LEAVE_TYPE', 'Paternity Leave', 'PATERNITY', 'HR', NOW(6), NOW(6)),
('LEAVE_TYPE', 'Compensatory Off', 'COMP_OFF', 'HR', NOW(6), NOW(6)),
('LEAVE_TYPE', 'Unpaid Leave', 'UNPAID', 'HR', NOW(6), NOW(6));

-- Organisations whose template catalog was already copied from the master list will not be re-seeded, so give
-- them the new rows now (organisations with no catalog yet get them on first read). IGNORE skips a row an admin
-- already added by hand under the same (organisation_id, category, name).
INSERT IGNORE INTO school_template_items (organisation_id, category, name, code, module_code, created_at, updated_at)
SELECT o.organisation_id, m.category, m.name, m.code, m.module_code, NOW(6), NOW(6)
FROM (SELECT DISTINCT organisation_id FROM school_template_items) o
JOIN school_template_master_items m ON m.category = 'LEAVE_TYPE';
