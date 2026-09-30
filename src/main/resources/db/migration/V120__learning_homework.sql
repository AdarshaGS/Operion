-- #226: homework assigned per section + subject, submitted on a student's behalf by a linked
-- guardian (students have no login of their own). One submission per (homework, student); a
-- teacher's review is feedback text, not a grade - day-to-day coursework, distinct from exams.

INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('LEARNING_VIEW', 'learning', 'View homework, quizzes, materials, lesson plans, answer sheets and leaderboards across all sections', NOW(6), NOW(6)),
    ('LEARNING_MANAGE', 'learning', 'Create and manage learning content and grant rewards for any section', NOW(6), NOW(6)),
    ('LESSON_PLAN_REVIEW', 'learning', 'Approve or reject submitted lesson plans', NOW(6), NOW(6));

CREATE TABLE homework (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id       BIGINT NOT NULL,
    section_id            BIGINT NOT NULL,
    subject_id            BIGINT NOT NULL,
    title                 VARCHAR(250) NOT NULL,
    description           VARCHAR(4000) NULL,
    due_date              DATE NOT NULL,
    attachment_ref        VARCHAR(255) NULL,
    assigned_by_person_id BIGINT NOT NULL,
    status                VARCHAR(20) NOT NULL,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    created_by            BIGINT,
    updated_by            BIGINT,
    CONSTRAINT fk_homework_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_homework_section FOREIGN KEY (section_id) REFERENCES sections (id),
    CONSTRAINT fk_homework_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_homework_assigned_by FOREIGN KEY (assigned_by_person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_homework_section ON homework (organisation_id, section_id, due_date);

CREATE TABLE homework_submissions (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id         BIGINT NOT NULL,
    homework_id             BIGINT NOT NULL,
    student_id              BIGINT NOT NULL,
    submitted_by_person_id  BIGINT NOT NULL,
    submission_text         VARCHAR(4000) NULL,
    attachment_ref          VARCHAR(255) NULL,
    submitted_at            DATETIME(6) NOT NULL,
    late                    BOOLEAN NOT NULL DEFAULT FALSE,
    status                  VARCHAR(20) NOT NULL,
    feedback                VARCHAR(2000) NULL,
    reviewed_at             DATETIME(6) NULL,
    created_at              DATETIME(6) NOT NULL,
    updated_at              DATETIME(6) NOT NULL,
    created_by              BIGINT,
    updated_by              BIGINT,
    CONSTRAINT uq_homework_submissions_student UNIQUE (homework_id, student_id),
    CONSTRAINT fk_homework_submissions_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_homework_submissions_homework FOREIGN KEY (homework_id) REFERENCES homework (id),
    CONSTRAINT fk_homework_submissions_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_homework_submissions_by FOREIGN KEY (submitted_by_person_id) REFERENCES persons (id)
) ENGINE = InnoDB;
