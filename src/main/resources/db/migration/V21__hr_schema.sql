-- HR module schema: StaffProfile, LeaveType, LeaveBalance, LeaveRequest, StaffDocument.
-- See ai-context/erp-system-plan.md §3.3 for the light sketch this deep-designed.
-- Payroll (PF/ESI/TDS) is deliberately out of scope - external-system-integration
-- territory. The StaffAttendance <-> approved-leave seam the sketch flags is also not
-- built yet: AttendanceStatus has no LEAVE value and LeaveRequest approval does not
-- write an attendance row.

CREATE TABLE staff_profiles (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    person_id          BIGINT NOT NULL,
    campus_id          BIGINT,
    employee_code      VARCHAR(30) NOT NULL,
    designation        VARCHAR(100) NOT NULL,
    department         VARCHAR(100),
    date_of_joining    DATE NOT NULL,
    employment_type    VARCHAR(20) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT uq_staff_profiles_org_employee_code UNIQUE (organisation_id, employee_code),
    CONSTRAINT fk_staff_profiles_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_staff_profiles_person FOREIGN KEY (person_id) REFERENCES persons (id),
    CONSTRAINT fk_staff_profiles_campus FOREIGN KEY (campus_id) REFERENCES campuses (id)
) ENGINE = InnoDB;

CREATE INDEX idx_staff_profiles_campus_status ON staff_profiles (organisation_id, campus_id, status);

CREATE TABLE leave_types (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id       BIGINT NOT NULL,
    code                  VARCHAR(30) NOT NULL,
    name                  VARCHAR(100) NOT NULL,
    default_annual_days   DOUBLE,
    status                VARCHAR(20) NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    created_by            BIGINT,
    updated_by            BIGINT,
    CONSTRAINT uq_leave_types_org_code UNIQUE (organisation_id, code),
    CONSTRAINT fk_leave_types_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE TABLE leave_balances (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    staff_profile_id    BIGINT NOT NULL,
    leave_type_id       BIGINT NOT NULL,
    academic_year_id    BIGINT NOT NULL,
    allocated_days      DOUBLE NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_leave_balances_staff_type_year UNIQUE (staff_profile_id, leave_type_id, academic_year_id),
    CONSTRAINT fk_leave_balances_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_leave_balances_staff_profile FOREIGN KEY (staff_profile_id) REFERENCES staff_profiles (id),
    CONSTRAINT fk_leave_balances_leave_type FOREIGN KEY (leave_type_id) REFERENCES leave_types (id),
    CONSTRAINT fk_leave_balances_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id)
) ENGINE = InnoDB;

CREATE TABLE leave_requests (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    staff_profile_id    BIGINT NOT NULL,
    leave_type_id       BIGINT NOT NULL,
    academic_year_id    BIGINT NOT NULL,
    start_date          DATE NOT NULL,
    end_date            DATE NOT NULL,
    number_of_days      DOUBLE NOT NULL,
    reason              VARCHAR(500),
    status              VARCHAR(20) NOT NULL,
    approved_by         BIGINT,
    decided_at          DATETIME(6),
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT fk_leave_requests_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_leave_requests_staff_profile FOREIGN KEY (staff_profile_id) REFERENCES staff_profiles (id),
    CONSTRAINT fk_leave_requests_leave_type FOREIGN KEY (leave_type_id) REFERENCES leave_types (id),
    CONSTRAINT fk_leave_requests_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id)
) ENGINE = InnoDB;

CREATE INDEX idx_leave_requests_staff_type_year_status ON leave_requests (organisation_id, staff_profile_id, leave_type_id, academic_year_id, status);

CREATE TABLE staff_documents (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id        BIGINT NOT NULL,
    staff_profile_id       BIGINT NOT NULL,
    document_type          VARCHAR(100) NOT NULL,
    file_reference          VARCHAR(500) NOT NULL,
    file_name              VARCHAR(255) NOT NULL,
    mime_type              VARCHAR(100),
    verification_status    VARCHAR(20) NOT NULL,
    verified_by            BIGINT,
    verified_at            DATETIME(6),
    status                 VARCHAR(20) NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    created_by             BIGINT,
    updated_by             BIGINT,
    CONSTRAINT fk_staff_documents_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_staff_documents_staff_profile FOREIGN KEY (staff_profile_id) REFERENCES staff_profiles (id)
) ENGINE = InnoDB;

CREATE INDEX idx_staff_documents_staff_status ON staff_documents (organisation_id, staff_profile_id, status);
