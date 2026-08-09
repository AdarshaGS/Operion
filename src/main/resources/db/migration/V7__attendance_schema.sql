-- Attendance module schema: StudentAttendance, ClassAttendanceRegister,
-- AttendanceCorrection, StaffAttendance. Daily-only for v1 - see
-- ai-context/erp-system-plan.md §3.1 for the design.

CREATE TABLE class_attendance_registers (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    academic_year_id  BIGINT NOT NULL,
    section_id        BIGINT NOT NULL,
    attendance_date   DATE NOT NULL,
    register_status   VARCHAR(20) NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_class_attendance_registers_section_date UNIQUE (section_id, attendance_date),
    CONSTRAINT fk_class_attendance_registers_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_class_attendance_registers_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id),
    CONSTRAINT fk_class_attendance_registers_section FOREIGN KEY (section_id) REFERENCES sections (id)
) ENGINE = InnoDB;

CREATE TABLE student_attendances (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id       BIGINT NOT NULL,
    student_enrollment_id BIGINT NOT NULL,
    academic_year_id      BIGINT NOT NULL,
    school_class_id       BIGINT NOT NULL,
    section_id            BIGINT NOT NULL,
    attendance_date       DATE NOT NULL,
    attendance_status     VARCHAR(20) NOT NULL,
    is_excused            BOOLEAN NOT NULL DEFAULT FALSE,
    remarks               VARCHAR(500),
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    created_by            BIGINT,
    updated_by            BIGINT,
    CONSTRAINT uq_student_attendances_enrollment_date UNIQUE (student_enrollment_id, attendance_date),
    CONSTRAINT fk_student_attendances_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_student_attendances_enrollment FOREIGN KEY (student_enrollment_id) REFERENCES student_enrollments (id),
    CONSTRAINT fk_student_attendances_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id),
    CONSTRAINT fk_student_attendances_school_class FOREIGN KEY (school_class_id) REFERENCES school_classes (id),
    CONSTRAINT fk_student_attendances_section FOREIGN KEY (section_id) REFERENCES sections (id)
) ENGINE = InnoDB;

CREATE INDEX idx_student_attendances_class_section_date ON student_attendances (organisation_id, school_class_id, section_id, attendance_date);
CREATE INDEX idx_student_attendances_org_year_date ON student_attendances (organisation_id, academic_year_id, attendance_date);

CREATE TABLE attendance_corrections (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id        BIGINT NOT NULL,
    student_attendance_id  BIGINT NOT NULL,
    previous_status        VARCHAR(20) NOT NULL,
    new_status             VARCHAR(20) NOT NULL,
    reason                 VARCHAR(500) NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    created_by             BIGINT,
    updated_by             BIGINT,
    CONSTRAINT fk_attendance_corrections_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_attendance_corrections_student_attendance FOREIGN KEY (student_attendance_id) REFERENCES student_attendances (id)
) ENGINE = InnoDB;

CREATE INDEX idx_attendance_corrections_student_attendance ON attendance_corrections (student_attendance_id);

CREATE TABLE staff_attendances (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    person_id         BIGINT NOT NULL,
    campus_id         BIGINT NOT NULL,
    attendance_date   DATE NOT NULL,
    attendance_status VARCHAR(20) NOT NULL,
    check_in_time     DATETIME(6),
    check_out_time    DATETIME(6),
    remarks           VARCHAR(500),
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_staff_attendances_person_date UNIQUE (person_id, attendance_date),
    CONSTRAINT fk_staff_attendances_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_staff_attendances_person FOREIGN KEY (person_id) REFERENCES persons (id),
    CONSTRAINT fk_staff_attendances_campus FOREIGN KEY (campus_id) REFERENCES campuses (id)
) ENGINE = InnoDB;

CREATE INDEX idx_staff_attendances_campus_date ON staff_attendances (organisation_id, campus_id, attendance_date);
