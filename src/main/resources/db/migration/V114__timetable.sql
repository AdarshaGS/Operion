-- #212: timetable data model. periods is the org-configured school-day structure (no
-- hardcoded period count/times); timetable_entries is one weekly-recurring slot per
-- (section, day, period) for an academic year. Clash rules are also enforced in the
-- service layer for readable messages - the unique keys are the concurrency backstop.

CREATE TABLE periods (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    ordinal          INT NOT NULL,
    label            VARCHAR(100) NOT NULL,
    start_time       TIME NOT NULL,
    end_time         TIME NOT NULL,
    is_break         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_periods_org_ordinal UNIQUE (organisation_id, ordinal),
    CONSTRAINT fk_periods_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE TABLE timetable_entries (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    academic_year_id   BIGINT NOT NULL,
    section_id         BIGINT NOT NULL,
    subject_id         BIGINT NOT NULL,
    teacher_person_id  BIGINT NOT NULL,
    day_of_week        VARCHAR(10) NOT NULL,
    period_id          BIGINT NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT uq_timetable_section_slot UNIQUE (organisation_id, section_id, day_of_week, period_id),
    CONSTRAINT uq_timetable_teacher_slot UNIQUE (organisation_id, academic_year_id, teacher_person_id, day_of_week, period_id),
    CONSTRAINT fk_timetable_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_timetable_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id),
    CONSTRAINT fk_timetable_section FOREIGN KEY (section_id) REFERENCES sections (id),
    CONSTRAINT fk_timetable_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_timetable_teacher FOREIGN KEY (teacher_person_id) REFERENCES persons (id),
    CONSTRAINT fk_timetable_period FOREIGN KEY (period_id) REFERENCES periods (id)
) ENGINE = InnoDB;

CREATE INDEX idx_timetable_teacher ON timetable_entries (organisation_id, teacher_person_id);

INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('TIMETABLE_VIEW', 'academic', 'View period structure and class/teacher timetables', NOW(6), NOW(6)),
    ('TIMETABLE_MANAGE', 'academic', 'Configure periods and build class timetables', NOW(6), NOW(6));
