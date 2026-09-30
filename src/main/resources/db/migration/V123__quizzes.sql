-- #227: teacher-authored multiple-choice quizzes. quiz_questions with a NULL quiz_id are the reusable
-- question bank (per subject); adding one to a quiz copies it, so editing the bank never changes a
-- quiz already in use. A student makes at most one attempt (submitted by a linked guardian on their
-- behalf) and is auto-scored on submit. Marks are whole numbers.

CREATE TABLE quizzes (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id        BIGINT NOT NULL,
    section_id             BIGINT NOT NULL,
    subject_id             BIGINT NOT NULL,
    title                  VARCHAR(250) NOT NULL,
    instructions           VARCHAR(2000) NULL,
    opens_at               DATETIME(6) NOT NULL,
    closes_at              DATETIME(6) NOT NULL,
    status                 VARCHAR(20) NOT NULL,
    created_by_person_id   BIGINT NOT NULL,
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,
    created_by             BIGINT,
    updated_by             BIGINT,
    CONSTRAINT fk_quizzes_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_quizzes_section FOREIGN KEY (section_id) REFERENCES sections (id),
    CONSTRAINT fk_quizzes_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_quizzes_author FOREIGN KEY (created_by_person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_quizzes_section ON quizzes (organisation_id, section_id);

CREATE TABLE quiz_questions (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id        BIGINT NOT NULL,
    quiz_id                BIGINT NULL,
    subject_id             BIGINT NOT NULL,
    ordinal                INT NOT NULL,
    question_text          VARCHAR(2000) NOT NULL,
    marks                  INT NOT NULL,
    created_by_person_id   BIGINT NOT NULL,
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,
    created_by             BIGINT,
    updated_by             BIGINT,
    CONSTRAINT fk_quiz_questions_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_quiz_questions_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes (id),
    CONSTRAINT fk_quiz_questions_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_quiz_questions_author FOREIGN KEY (created_by_person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_quiz_questions_quiz ON quiz_questions (organisation_id, quiz_id);
CREATE INDEX idx_quiz_questions_bank ON quiz_questions (organisation_id, subject_id);

CREATE TABLE quiz_options (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id        BIGINT NOT NULL,
    question_id            BIGINT NOT NULL,
    ordinal                INT NOT NULL,
    option_text            VARCHAR(500) NOT NULL,
    correct                BOOLEAN NOT NULL DEFAULT FALSE,
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,
    created_by             BIGINT,
    updated_by             BIGINT,
    CONSTRAINT fk_quiz_options_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_quiz_options_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id)
) ENGINE = InnoDB;

CREATE INDEX idx_quiz_options_question ON quiz_options (question_id);

CREATE TABLE quiz_attempts (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id         BIGINT NOT NULL,
    quiz_id                 BIGINT NOT NULL,
    student_id              BIGINT NOT NULL,
    submitted_by_person_id  BIGINT NOT NULL,
    submitted_at            DATETIME(6) NOT NULL,
    score                   INT NOT NULL,
    max_score               INT NOT NULL,
    created_at              DATETIME(6) NOT NULL,
    updated_at              DATETIME(6) NOT NULL,
    created_by              BIGINT,
    updated_by              BIGINT,
    CONSTRAINT uq_quiz_attempts_student UNIQUE (quiz_id, student_id),
    CONSTRAINT fk_quiz_attempts_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_quiz_attempts_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes (id),
    CONSTRAINT fk_quiz_attempts_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_quiz_attempts_by FOREIGN KEY (submitted_by_person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_quiz_attempts_student ON quiz_attempts (organisation_id, student_id);

CREATE TABLE quiz_attempt_answers (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    attempt_id          BIGINT NOT NULL,
    question_id         BIGINT NOT NULL,
    selected_option_id  BIGINT NULL,
    correct             BOOLEAN NOT NULL,
    marks_awarded       INT NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_quiz_attempt_answers UNIQUE (attempt_id, question_id),
    CONSTRAINT fk_quiz_attempt_answers_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_quiz_attempt_answers_attempt FOREIGN KEY (attempt_id) REFERENCES quiz_attempts (id),
    CONSTRAINT fk_quiz_attempt_answers_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id),
    CONSTRAINT fk_quiz_attempt_answers_option FOREIGN KEY (selected_option_id) REFERENCES quiz_options (id)
) ENGINE = InnoDB;
