-- Teacher performance, phases 2-4: engagement/outcome metrics need a day threshold on the definition and a
-- register submit timestamp; teacher self-view is an opt-in setting; appraisal cycles carry reviewer ratings and goals.

-- Phase 2: a metric that needs a day threshold (e.g. "marks entered within N days of the exam") keeps it here.
ALTER TABLE kpi_definitions ADD COLUMN param_days INT NULL;

-- Phase 2: when a class attendance register was submitted. Existing rows stay NULL (unknown), not "on time".
ALTER TABLE class_attendance_registers ADD COLUMN submitted_at DATETIME(6) NULL;

-- Phase 2: teachers see their own scorecard only when the organisation turns this on.
CREATE TABLE kpi_settings (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    teacher_self_view TINYINT(1) NOT NULL DEFAULT 0,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_kpi_settings_organisation UNIQUE (organisation_id)
) ENGINE = InnoDB;

-- Phase 4: reviewing is separate from viewing and managing.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('KPI_REVIEW', 'performance', 'Give appraisal ratings and set goals for teachers', NOW(6), NOW(6));

CREATE TABLE appraisal_cycles (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    name            VARCHAR(150) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    status          VARCHAR(20) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    created_by      BIGINT,
    updated_by      BIGINT,
    CONSTRAINT uq_appraisal_cycles_org_name UNIQUE (organisation_id, name)
) ENGINE = InnoDB;

-- One rating per reviewer per teacher per cycle; the reviewer's own row (reviewer = teacher) is the self-appraisal.
CREATE TABLE teacher_feedback (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    cycle_id            BIGINT NOT NULL,
    teacher_person_id   BIGINT NOT NULL,
    reviewer_person_id  BIGINT NOT NULL,
    source              VARCHAR(20) NOT NULL,
    rating              INT NOT NULL,
    comment             VARCHAR(2000) NULL,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT fk_teacher_feedback_cycle FOREIGN KEY (cycle_id) REFERENCES appraisal_cycles (id),
    CONSTRAINT fk_teacher_feedback_teacher FOREIGN KEY (teacher_person_id) REFERENCES persons (id),
    CONSTRAINT fk_teacher_feedback_reviewer FOREIGN KEY (reviewer_person_id) REFERENCES persons (id),
    CONSTRAINT uq_teacher_feedback_reviewer UNIQUE (cycle_id, teacher_person_id, reviewer_person_id)
) ENGINE = InnoDB;

CREATE TABLE teacher_goals (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    cycle_id          BIGINT NOT NULL,
    teacher_person_id BIGINT NOT NULL,
    title             VARCHAR(250) NOT NULL,
    description       VARCHAR(2000) NULL,
    status            VARCHAR(20) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_teacher_goals_cycle FOREIGN KEY (cycle_id) REFERENCES appraisal_cycles (id),
    CONSTRAINT fk_teacher_goals_teacher FOREIGN KEY (teacher_person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_teacher_feedback_teacher ON teacher_feedback (organisation_id, teacher_person_id, cycle_id);
CREATE INDEX idx_teacher_goals_teacher ON teacher_goals (organisation_id, teacher_person_id, cycle_id);
