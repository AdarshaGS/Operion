-- Migrates StaffProfile.designation/department from free-text to FK references into
-- the departments/designations catalogs added in V32.

ALTER TABLE staff_profiles
    ADD COLUMN designation_id BIGINT,
    ADD COLUMN department_id  BIGINT;

INSERT INTO designations (organisation_id, name, status, created_at, updated_at)
SELECT DISTINCT sp.organisation_id, sp.designation, 'ACTIVE', NOW(6), NOW(6)
FROM staff_profiles sp
WHERE NOT EXISTS (
    SELECT 1 FROM designations d WHERE d.organisation_id = sp.organisation_id AND d.name = sp.designation
);

UPDATE staff_profiles sp
JOIN designations d ON d.organisation_id = sp.organisation_id AND d.name = sp.designation
SET sp.designation_id = d.id;

INSERT INTO departments (organisation_id, name, status, created_at, updated_at)
SELECT DISTINCT sp.organisation_id, sp.department, 'ACTIVE', NOW(6), NOW(6)
FROM staff_profiles sp
WHERE sp.department IS NOT NULL AND sp.department <> '' AND NOT EXISTS (
    SELECT 1 FROM departments dpt WHERE dpt.organisation_id = sp.organisation_id AND dpt.name = sp.department
);

UPDATE staff_profiles sp
JOIN departments dpt ON dpt.organisation_id = sp.organisation_id AND dpt.name = sp.department
SET sp.department_id = dpt.id
WHERE sp.department IS NOT NULL AND sp.department <> '';

ALTER TABLE staff_profiles
    DROP COLUMN designation,
    DROP COLUMN department,
    MODIFY COLUMN designation_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_staff_profiles_designation FOREIGN KEY (designation_id) REFERENCES designations (id),
    ADD CONSTRAINT fk_staff_profiles_department FOREIGN KEY (department_id) REFERENCES departments (id);
