-- #229: lesson plans per section/subject/date. DRAFT -> SUBMITTED -> APPROVED | REJECTED; a rejected
-- plan goes back to the teacher, who edits and resubmits. Approved plans are locked and feed the calendar.

CREATE TABLE lesson_plans (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id        BIGINT NOT NULL,
    section_id             BIGINT NOT NULL,
    subject_id             BIGINT NOT NULL,
    plan_date              DATE NOT NULL,
    title                  VARCHAR(250) NOT NULL,
    objectives             VARCHAR(2000) NULL,
    activities             VARCHAR(4000) NULL,
    resources              VARCHAR(2000) NULL,
    status                 VARCHAR(20) NOT NULL,
    submitted_by_person_id BIGINT NOT NULL,
    submitted_at           DATETIME(6) NULL,
    review_note            VARCHAR(2000) NULL,
    reviewed_by_person_id  BIGINT NULL,
    reviewed_at            DATETIME(6) NULL,
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,
    created_by             BIGINT,
    updated_by             BIGINT,
    CONSTRAINT fk_lesson_plans_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_lesson_plans_section FOREIGN KEY (section_id) REFERENCES sections (id),
    CONSTRAINT fk_lesson_plans_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_lesson_plans_author FOREIGN KEY (submitted_by_person_id) REFERENCES persons (id),
    CONSTRAINT fk_lesson_plans_reviewer FOREIGN KEY (reviewed_by_person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_lesson_plans_section_date ON lesson_plans (organisation_id, section_id, plan_date);
