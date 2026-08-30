ALTER TABLE students ADD COLUMN medical_alerts VARCHAR(1000) NULL;

CREATE TABLE student_applications (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id        BIGINT NOT NULL,
    applicant_name         VARCHAR(255) NOT NULL,
    date_of_birth          DATE,
    gender                 VARCHAR(20),
    guardian_name          VARCHAR(255),
    guardian_phone         VARCHAR(30),
    desired_grade_level_id BIGINT,
    notes                  VARCHAR(1000),
    status                 VARCHAR(20)  NOT NULL,
    applied_at             DATETIME(6)  NOT NULL,
    decided_by             BIGINT,
    decided_at             DATETIME(6),
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    created_by             BIGINT,
    updated_by             BIGINT,
    CONSTRAINT fk_student_applications_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_student_applications_grade_level FOREIGN KEY (desired_grade_level_id) REFERENCES grade_levels (id)
) ENGINE = InnoDB;

CREATE INDEX idx_student_applications_status ON student_applications (organisation_id, status);

-- STUDENT_SENSITIVE_VIEW gates category/medical_alerts on top of STUDENT_VIEW (#114);
-- STUDENT_APPLICATION_MANAGE covers the whole pre-admission inquiry pipeline.
INSERT IGNORE INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('STUDENT_SENSITIVE_VIEW',    'student', 'View sensitive student fields (category, medical alerts)', NOW(6), NOW(6)),
    ('STUDENT_APPLICATION_MANAGE', 'student', 'Record and decide prospective-student applications',      NOW(6), NOW(6));
