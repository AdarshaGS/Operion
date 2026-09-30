-- #221/#222: payroll foundation. salary_components is the org-defined catalog of earnings and
-- deductions (no rows seeded, nothing pre-named after any country's statutory scheme);
-- salary_structures is one effective-dated, insert-only compensation row per staff member
-- (a raise adds a new row, history is never rewritten); salary_structure_lines is each
-- component's value within a structure. Bank/tax details already live in staff_bank_details.

CREATE TABLE salary_components (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    name               VARCHAR(100) NOT NULL,
    component_type     VARCHAR(20) NOT NULL,
    calculation_basis  VARCHAR(30) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT uq_salary_components_org_name UNIQUE (organisation_id, name),
    CONSTRAINT fk_salary_components_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE TABLE salary_structures (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    staff_profile_id  BIGINT NOT NULL,
    effective_from    DATE NOT NULL,
    basic_amount      DECIMAL(14, 2) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_salary_structures_staff_effective UNIQUE (organisation_id, staff_profile_id, effective_from),
    CONSTRAINT fk_salary_structures_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_salary_structures_staff FOREIGN KEY (staff_profile_id) REFERENCES staff_profiles (id)
) ENGINE = InnoDB;

CREATE TABLE salary_structure_lines (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id      BIGINT NOT NULL,
    salary_structure_id  BIGINT NOT NULL,
    salary_component_id  BIGINT NOT NULL,
    line_value           DECIMAL(14, 4) NOT NULL,
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    created_by           BIGINT,
    updated_by           BIGINT,
    CONSTRAINT uq_salary_structure_lines_component UNIQUE (salary_structure_id, salary_component_id),
    CONSTRAINT fk_salary_structure_lines_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_salary_structure_lines_structure FOREIGN KEY (salary_structure_id) REFERENCES salary_structures (id),
    CONSTRAINT fk_salary_structure_lines_component FOREIGN KEY (salary_component_id) REFERENCES salary_components (id)
) ENGINE = InnoDB;

INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('PAYROLL_VIEW', 'hr', 'View salary components, salary structures, payroll runs and payslips', NOW(6), NOW(6)),
    ('PAYROLL_MANAGE', 'hr', 'Configure salary components and structures, generate and finalize payroll runs', NOW(6), NOW(6));
