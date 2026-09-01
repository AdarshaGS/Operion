-- Introduces FeeStructureGroup (#129): the named "one fee structure, several components"
-- setup an admin configures once per class per year (e.g. "Grade 5 Annual Fees 2026-27").
-- fee_structures rows become the components underneath it - fee_category_id + amount only,
-- no longer carrying academic_year_id/school_class_id directly. Class-level, not
-- section-level, per the same convention as class_subjects.

CREATE TABLE fee_structure_groups (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    academic_year_id  BIGINT NOT NULL,
    school_class_id   BIGINT NOT NULL,
    name              VARCHAR(150) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_fee_structure_groups_year_class UNIQUE (organisation_id, academic_year_id, school_class_id),
    CONSTRAINT fk_fee_structure_groups_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_fee_structure_groups_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id),
    CONSTRAINT fk_fee_structure_groups_school_class FOREIGN KEY (school_class_id) REFERENCES school_classes (id)
) ENGINE = InnoDB;

-- Backfill: one group per (organisation, academic year, class) combination that already
-- has fee_structures rows, named from the grade level + academic year so existing data
-- (dev/demo only - this module has no production usage yet) doesn't end up unnamed.
INSERT INTO fee_structure_groups (organisation_id, academic_year_id, school_class_id, name, status, created_at, updated_at)
SELECT DISTINCT fs.organisation_id, fs.academic_year_id, fs.school_class_id,
       CONCAT(gl.name, ' Fees ', ay.name), 'ACTIVE', NOW(6), NOW(6)
FROM fee_structures fs
JOIN school_classes sc ON sc.id = fs.school_class_id
JOIN grade_levels gl ON gl.id = sc.grade_level_id
JOIN academic_years ay ON ay.id = fs.academic_year_id;

ALTER TABLE fee_structures ADD COLUMN fee_structure_group_id BIGINT;

UPDATE fee_structures fs
JOIN fee_structure_groups g
    ON g.organisation_id = fs.organisation_id
    AND g.academic_year_id = fs.academic_year_id
    AND g.school_class_id = fs.school_class_id
SET fs.fee_structure_group_id = g.id;

ALTER TABLE fee_structures
    MODIFY COLUMN fee_structure_group_id BIGINT NOT NULL,
    DROP INDEX uq_fee_structures_year_class_category,
    DROP FOREIGN KEY fk_fee_structures_academic_year,
    DROP FOREIGN KEY fk_fee_structures_school_class,
    DROP COLUMN academic_year_id,
    DROP COLUMN school_class_id,
    ADD CONSTRAINT uq_fee_structures_group_category UNIQUE (organisation_id, fee_structure_group_id, fee_category_id),
    ADD CONSTRAINT fk_fee_structures_group FOREIGN KEY (fee_structure_group_id) REFERENCES fee_structure_groups (id);
