-- Student Management module schema: Student, StudentEnrollment, StudentDocument,
-- StudentExit, Guardian, StudentGuardian. See ai-context/erp-system-plan.md §2.2-2.3
-- for the design. StudentAdmission fields are merged onto students (no separate table)
-- per the MVP decision recorded there.

CREATE TABLE students (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    person_id         BIGINT NOT NULL,
    admission_number  VARCHAR(50) NOT NULL,
    admission_date    DATE NOT NULL,
    admission_source  VARCHAR(50),
    previous_school   VARCHAR(255),
    tc_number         VARCHAR(50),
    entrance_score    DECIMAL(6, 2),
    blood_group       VARCHAR(10),
    category          VARCHAR(50),
    nationality       VARCHAR(50),
    remarks           VARCHAR(500),
    status            VARCHAR(20)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_students_person UNIQUE (person_id),
    CONSTRAINT uq_students_org_admission_number UNIQUE (organisation_id, admission_number),
    CONSTRAINT fk_students_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_students_person FOREIGN KEY (person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE TABLE student_enrollments (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    student_id        BIGINT NOT NULL,
    academic_year_id  BIGINT NOT NULL,
    section_id        BIGINT NOT NULL,
    roll_number       INT,
    enrollment_status VARCHAR(20) NOT NULL,
    is_current        BOOLEAN NOT NULL DEFAULT TRUE,
    enrolled_date     DATE NOT NULL,
    exit_date         DATE,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_student_enrollments_student_year UNIQUE (student_id, academic_year_id),
    CONSTRAINT uq_student_enrollments_section_roll UNIQUE (section_id, roll_number),
    CONSTRAINT fk_student_enrollments_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_student_enrollments_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_student_enrollments_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id),
    CONSTRAINT fk_student_enrollments_section FOREIGN KEY (section_id) REFERENCES sections (id)
) ENGINE = InnoDB;

CREATE INDEX idx_student_enrollments_student_current ON student_enrollments (student_id, is_current);
CREATE INDEX idx_student_enrollments_section ON student_enrollments (section_id);
CREATE INDEX idx_student_enrollments_org_year ON student_enrollments (organisation_id, academic_year_id);

CREATE TABLE student_documents (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    student_id          BIGINT NOT NULL,
    document_type       VARCHAR(50) NOT NULL,
    file_reference      VARCHAR(512) NOT NULL,
    file_name           VARCHAR(255) NOT NULL,
    mime_type           VARCHAR(100),
    verification_status VARCHAR(20) NOT NULL,
    verified_by         BIGINT,
    verified_at         DATETIME(6),
    status              VARCHAR(20)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT fk_student_documents_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_student_documents_student FOREIGN KEY (student_id) REFERENCES students (id)
) ENGINE = InnoDB;

CREATE INDEX idx_student_documents_student ON student_documents (student_id);

CREATE TABLE student_exits (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    student_id         BIGINT NOT NULL,
    exit_type          VARCHAR(20) NOT NULL,
    exit_date          DATE NOT NULL,
    reason             VARCHAR(500),
    destination_school VARCHAR(255),
    initiated_by       BIGINT,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT fk_student_exits_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_student_exits_student FOREIGN KEY (student_id) REFERENCES students (id)
) ENGINE = InnoDB;

CREATE INDEX idx_student_exits_student ON student_exits (student_id);

CREATE TABLE guardians (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    person_id        BIGINT NOT NULL,
    occupation       VARCHAR(100),
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_guardians_person UNIQUE (person_id),
    CONSTRAINT fk_guardians_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_guardians_person FOREIGN KEY (person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE TABLE student_guardians (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id           BIGINT NOT NULL,
    student_id                BIGINT NOT NULL,
    guardian_id               BIGINT NOT NULL,
    relationship_type         VARCHAR(30) NOT NULL,
    is_primary_guardian       BOOLEAN NOT NULL DEFAULT FALSE,
    is_emergency_contact      BOOLEAN NOT NULL DEFAULT FALSE,
    can_pickup                BOOLEAN NOT NULL DEFAULT FALSE,
    can_receive_communication BOOLEAN NOT NULL DEFAULT TRUE,
    contact_priority          INT NOT NULL DEFAULT 0,
    status                    VARCHAR(20)  NOT NULL,
    created_at                DATETIME(6)  NOT NULL,
    updated_at                DATETIME(6)  NOT NULL,
    created_by                BIGINT,
    updated_by                BIGINT,
    CONSTRAINT uq_student_guardians_student_guardian UNIQUE (student_id, guardian_id),
    CONSTRAINT fk_student_guardians_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_student_guardians_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_student_guardians_guardian FOREIGN KEY (guardian_id) REFERENCES guardians (id)
) ENGINE = InnoDB;

CREATE INDEX idx_student_guardians_student ON student_guardians (student_id);
CREATE INDEX idx_student_guardians_guardian ON student_guardians (guardian_id);
CREATE INDEX idx_student_guardians_student_priority ON student_guardians (student_id, contact_priority);
