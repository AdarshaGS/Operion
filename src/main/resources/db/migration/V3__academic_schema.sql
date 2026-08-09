-- Academic Foundation module schema: GradeLevel, Subject, SchoolClass, Section,
-- ClassSubject, TeacherAssignment. AcademicYear stays in Foundation (V1) - this module
-- only consumes it. See ai-context/erp-system-plan.md §2.1 for the design.

CREATE TABLE grade_levels (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    name             VARCHAR(100) NOT NULL,
    sequence_order   INT NOT NULL,
    stage            VARCHAR(50),
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_grade_levels_org_sequence UNIQUE (organisation_id, sequence_order),
    CONSTRAINT uq_grade_levels_org_name UNIQUE (organisation_id, name),
    CONSTRAINT fk_grade_levels_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE TABLE subjects (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    name             VARCHAR(100) NOT NULL,
    code             VARCHAR(20),
    is_elective      BOOLEAN NOT NULL DEFAULT FALSE,
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_subjects_org_name UNIQUE (organisation_id, name),
    CONSTRAINT fk_subjects_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE TABLE school_classes (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    academic_year_id BIGINT NOT NULL,
    campus_id        BIGINT NOT NULL,
    grade_level_id   BIGINT NOT NULL,
    display_name     VARCHAR(100),
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_school_classes_year_campus_grade UNIQUE (academic_year_id, campus_id, grade_level_id),
    CONSTRAINT fk_school_classes_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_school_classes_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id),
    CONSTRAINT fk_school_classes_campus FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_school_classes_grade_level FOREIGN KEY (grade_level_id) REFERENCES grade_levels (id)
) ENGINE = InnoDB;

CREATE INDEX idx_school_classes_organisation ON school_classes (organisation_id);
CREATE INDEX idx_school_classes_academic_year ON school_classes (academic_year_id);

CREATE TABLE sections (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    school_class_id  BIGINT NOT NULL,
    name             VARCHAR(50) NOT NULL,
    capacity         INT,
    room             VARCHAR(50),
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_sections_class_name UNIQUE (school_class_id, name),
    CONSTRAINT fk_sections_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_sections_school_class FOREIGN KEY (school_class_id) REFERENCES school_classes (id)
) ENGINE = InnoDB;

CREATE INDEX idx_sections_organisation ON sections (organisation_id);

CREATE TABLE class_subjects (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    school_class_id  BIGINT NOT NULL,
    subject_id       BIGINT NOT NULL,
    is_mandatory     BOOLEAN NOT NULL DEFAULT TRUE,
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_class_subjects_class_subject UNIQUE (school_class_id, subject_id),
    CONSTRAINT fk_class_subjects_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_class_subjects_school_class FOREIGN KEY (school_class_id) REFERENCES school_classes (id),
    CONSTRAINT fk_class_subjects_subject FOREIGN KEY (subject_id) REFERENCES subjects (id)
) ENGINE = InnoDB;

CREATE INDEX idx_class_subjects_school_class ON class_subjects (school_class_id);

CREATE TABLE teacher_assignments (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    academic_year_id  BIGINT NOT NULL,
    section_id        BIGINT NOT NULL,
    subject_id        BIGINT,
    teacher_person_id BIGINT NOT NULL,
    assignment_type   VARCHAR(20) NOT NULL,
    start_date        DATE NOT NULL,
    end_date          DATE,
    status            VARCHAR(20)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_teacher_assignments_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_teacher_assignments_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id),
    CONSTRAINT fk_teacher_assignments_section FOREIGN KEY (section_id) REFERENCES sections (id),
    CONSTRAINT fk_teacher_assignments_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_teacher_assignments_person FOREIGN KEY (teacher_person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_teacher_assignments_section_status ON teacher_assignments (section_id, status);
CREATE INDEX idx_teacher_assignments_teacher_status ON teacher_assignments (teacher_person_id, status);
CREATE INDEX idx_teacher_assignments_academic_year ON teacher_assignments (academic_year_id);
