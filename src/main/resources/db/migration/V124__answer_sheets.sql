-- #230: scanned answer sheets per (exam schedule, student enrollment), marked on-screen by the
-- teacher and released back to the guardian portal. UPLOADED -> MARKED -> RELEASED; released
-- sheets are locked. Annotation coordinates are fractions (0..1) of the page image so they stay
-- valid at any render size. Total marks are entered/derived here and NOT auto-posted to the
-- marks register (marks_entries) - that stays an explicit examination step.

CREATE TABLE answer_sheets (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id       BIGINT NOT NULL,
    exam_schedule_id      BIGINT NOT NULL,
    student_enrollment_id BIGINT NOT NULL,
    status                VARCHAR(20) NOT NULL,
    total_marks           DOUBLE NULL,
    overall_remarks       VARCHAR(2000) NULL,
    uploaded_by_person_id BIGINT NOT NULL,
    marked_by_person_id   BIGINT NULL,
    marked_at             DATETIME(6) NULL,
    released_at           DATETIME(6) NULL,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    created_by            BIGINT,
    updated_by            BIGINT,
    CONSTRAINT fk_answer_sheets_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_answer_sheets_schedule FOREIGN KEY (exam_schedule_id) REFERENCES exam_schedules (id),
    CONSTRAINT fk_answer_sheets_enrollment FOREIGN KEY (student_enrollment_id) REFERENCES student_enrollments (id),
    CONSTRAINT fk_answer_sheets_uploader FOREIGN KEY (uploaded_by_person_id) REFERENCES persons (id),
    CONSTRAINT fk_answer_sheets_marker FOREIGN KEY (marked_by_person_id) REFERENCES persons (id),
    CONSTRAINT uq_answer_sheets_schedule_enrollment UNIQUE (exam_schedule_id, student_enrollment_id)
) ENGINE = InnoDB;

CREATE TABLE answer_sheet_pages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    sheet_id        BIGINT NOT NULL,
    page_number     INT NOT NULL,
    asset_ref       VARCHAR(255) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    created_by      BIGINT,
    updated_by      BIGINT,
    CONSTRAINT fk_answer_sheet_pages_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_answer_sheet_pages_sheet FOREIGN KEY (sheet_id) REFERENCES answer_sheets (id)
) ENGINE = InnoDB;

CREATE TABLE answer_sheet_annotations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    page_id         BIGINT NOT NULL,
    kind            VARCHAR(20) NOT NULL,
    pos_x           DOUBLE NOT NULL,
    pos_y           DOUBLE NOT NULL,
    label           VARCHAR(500) NULL,
    marks           DOUBLE NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    created_by      BIGINT,
    updated_by      BIGINT,
    CONSTRAINT fk_answer_sheet_annotations_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_answer_sheet_annotations_page FOREIGN KEY (page_id) REFERENCES answer_sheet_pages (id)
) ENGINE = InnoDB;

CREATE INDEX idx_answer_sheets_enrollment ON answer_sheets (organisation_id, student_enrollment_id);
CREATE INDEX idx_answer_sheet_pages_sheet ON answer_sheet_pages (sheet_id);
CREATE INDEX idx_answer_sheet_annotations_page ON answer_sheet_annotations (page_id);
