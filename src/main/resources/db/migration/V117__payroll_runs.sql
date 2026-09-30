-- #223: monthly payroll runs. A run is DRAFT (adjustable, discardable) until finalized, after
-- which it is locked; corrections are a new run for the same month that supersedes it once
-- finalized. Payslips snapshot the staff member's name/code/designation/department and every
-- earning and deduction as lines, so later structure/component/staff edits never change a
-- finalized payslip. At most one non-superseded run per month is enforced in the service.

CREATE TABLE payroll_runs (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id      BIGINT NOT NULL,
    pay_year             INT NOT NULL,
    pay_month            INT NOT NULL,
    status               VARCHAR(20) NOT NULL,
    supersedes_run_id    BIGINT NULL,
    finalized_at         DATETIME(6) NULL,
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    created_by           BIGINT,
    updated_by           BIGINT,
    CONSTRAINT fk_payroll_runs_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_payroll_runs_supersedes FOREIGN KEY (supersedes_run_id) REFERENCES payroll_runs (id)
) ENGINE = InnoDB;

CREATE INDEX idx_payroll_runs_period ON payroll_runs (organisation_id, pay_year, pay_month);

CREATE TABLE payslips (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    payroll_run_id     BIGINT NOT NULL,
    staff_profile_id   BIGINT NOT NULL,
    employee_code      VARCHAR(100) NOT NULL,
    staff_name         VARCHAR(250) NOT NULL,
    designation_name   VARCHAR(200) NULL,
    department_name    VARCHAR(200) NULL,
    is_teacher         BOOLEAN NOT NULL DEFAULT FALSE,
    basic_amount       DECIMAL(14, 2) NOT NULL,
    gross_earnings     DECIMAL(14, 2) NOT NULL,
    total_deductions   DECIMAL(14, 2) NOT NULL,
    net_pay            DECIMAL(14, 2) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT uq_payslips_run_staff UNIQUE (payroll_run_id, staff_profile_id),
    CONSTRAINT fk_payslips_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_payslips_run FOREIGN KEY (payroll_run_id) REFERENCES payroll_runs (id),
    CONSTRAINT fk_payslips_staff FOREIGN KEY (staff_profile_id) REFERENCES staff_profiles (id)
) ENGINE = InnoDB;

CREATE INDEX idx_payslips_staff ON payslips (organisation_id, staff_profile_id);

CREATE TABLE payslip_lines (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    payslip_id       BIGINT NOT NULL,
    label            VARCHAR(200) NOT NULL,
    line_type        VARCHAR(20) NOT NULL,
    source           VARCHAR(20) NOT NULL,
    amount           DECIMAL(14, 2) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT fk_payslip_lines_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_payslip_lines_payslip FOREIGN KEY (payslip_id) REFERENCES payslips (id)
) ENGINE = InnoDB;
