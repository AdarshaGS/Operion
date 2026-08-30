-- HR workforce lifecycle (GitHub milestone #24, issues #178-#184): address on Person,
-- reporting-manager self-reference and assignment history on StaffProfile, staff exits,
-- staff bank/tax details behind a dedicated permission, and document expiry.

ALTER TABLE persons
    ADD COLUMN address VARCHAR(500);

ALTER TABLE staff_profiles
    ADD COLUMN reporting_manager_id BIGINT,
    ADD CONSTRAINT fk_staff_profiles_reporting_manager FOREIGN KEY (reporting_manager_id) REFERENCES staff_profiles (id);

ALTER TABLE staff_documents
    ADD COLUMN expiry_date DATE;

-- Insert-only assignment history, same shape/lifecycle as teacher_assignments: a
-- transfer or designation change ends the open row and inserts a new one.
-- StaffProfile keeps its own campus/department/designation columns as the current
-- snapshot; this table is the trail of who was assigned where, and when.
CREATE TABLE staff_assignments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    staff_profile_id    BIGINT NOT NULL,
    campus_id           BIGINT,
    department_id       BIGINT,
    designation_id      BIGINT NOT NULL,
    start_date          DATE NOT NULL,
    end_date            DATE,
    status              VARCHAR(20) NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT fk_staff_assignments_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_staff_assignments_staff_profile FOREIGN KEY (staff_profile_id) REFERENCES staff_profiles (id),
    CONSTRAINT fk_staff_assignments_campus FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_staff_assignments_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_staff_assignments_designation FOREIGN KEY (designation_id) REFERENCES designations (id)
) ENGINE = InnoDB;

CREATE INDEX idx_staff_assignments_staff_status ON staff_assignments (organisation_id, staff_profile_id, status);

-- Insert-only exit event log, same shape as student_exits - no one-per-staff
-- uniqueness, a staff member could resign and later be re-hired.
CREATE TABLE staff_exits (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    staff_profile_id    BIGINT NOT NULL,
    exit_type           VARCHAR(20) NOT NULL,
    exit_date           DATE NOT NULL,
    reason              VARCHAR(500),
    initiated_by        BIGINT,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT fk_staff_exits_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_staff_exits_staff_profile FOREIGN KEY (staff_profile_id) REFERENCES staff_profiles (id)
) ENGINE = InnoDB;

-- 1:1 with staff_profiles, split into its own table so it can be gated by
-- HR_PAYROLL_VIEW separately from ordinary HR_VIEW/HR_STAFF_MANAGE access.
CREATE TABLE staff_bank_details (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id             BIGINT NOT NULL,
    staff_profile_id            BIGINT NOT NULL,
    bank_account_holder_name    VARCHAR(150),
    bank_account_number         VARCHAR(50),
    bank_name                   VARCHAR(150),
    bank_branch_code            VARCHAR(30),
    tax_identifier              VARCHAR(100),
    created_at                  DATETIME(6)  NOT NULL,
    updated_at                  DATETIME(6)  NOT NULL,
    created_by                  BIGINT,
    updated_by                  BIGINT,
    CONSTRAINT uq_staff_bank_details_staff_profile UNIQUE (staff_profile_id),
    CONSTRAINT fk_staff_bank_details_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_staff_bank_details_staff_profile FOREIGN KEY (staff_profile_id) REFERENCES staff_profiles (id)
) ENGINE = InnoDB;

INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('HR_PAYROLL_VIEW', 'hr', 'View and manage staff bank and tax details', NOW(6), NOW(6));
