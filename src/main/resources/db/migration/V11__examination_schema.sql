-- Examination module schema: Exam, ExamSchedule, GradingScale, GradingScaleBand,
-- MarksEntry, ReportCard. See ai-context/erp-system-plan.md §3.3 for the light sketch
-- this deep-designed (Examinations was flagged build-later, not fully speced there).

CREATE TABLE exams (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    academic_year_id  BIGINT NOT NULL,
    name              VARCHAR(100) NOT NULL,
    exam_type         VARCHAR(20) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_exams_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_exams_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id)
) ENGINE = InnoDB;

CREATE INDEX idx_exams_academic_year ON exams (academic_year_id);

CREATE TABLE exam_schedules (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    exam_id           BIGINT NOT NULL,
    school_class_id   BIGINT NOT NULL,
    subject_id        BIGINT NOT NULL,
    exam_date         DATE NOT NULL,
    max_marks         DOUBLE NOT NULL,
    pass_marks        DOUBLE NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_exam_schedules_exam_class_subject UNIQUE (exam_id, school_class_id, subject_id),
    CONSTRAINT fk_exam_schedules_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_exam_schedules_exam FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT fk_exam_schedules_school_class FOREIGN KEY (school_class_id) REFERENCES school_classes (id),
    CONSTRAINT fk_exam_schedules_subject FOREIGN KEY (subject_id) REFERENCES subjects (id)
) ENGINE = InnoDB;

CREATE TABLE grading_scales (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    name              VARCHAR(100) NOT NULL,
    is_default        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_grading_scales_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE TABLE grading_scale_bands (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    grading_scale_id   BIGINT NOT NULL,
    grade              VARCHAR(10) NOT NULL,
    min_percentage     DOUBLE NOT NULL,
    remark             VARCHAR(255),
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT uq_grading_scale_bands_scale_grade UNIQUE (grading_scale_id, grade),
    CONSTRAINT uq_grading_scale_bands_scale_min UNIQUE (grading_scale_id, min_percentage),
    CONSTRAINT fk_grading_scale_bands_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_grading_scale_bands_scale FOREIGN KEY (grading_scale_id) REFERENCES grading_scales (id)
) ENGINE = InnoDB;

CREATE TABLE marks_entries (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id         BIGINT NOT NULL,
    exam_schedule_id        BIGINT NOT NULL,
    student_enrollment_id   BIGINT NOT NULL,
    marks_obtained          DOUBLE NOT NULL,
    is_absent               BOOLEAN NOT NULL DEFAULT FALSE,
    remarks                 VARCHAR(500),
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    created_by              BIGINT,
    updated_by              BIGINT,
    CONSTRAINT uq_marks_entries_schedule_enrollment UNIQUE (exam_schedule_id, student_enrollment_id),
    CONSTRAINT fk_marks_entries_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_marks_entries_schedule FOREIGN KEY (exam_schedule_id) REFERENCES exam_schedules (id),
    CONSTRAINT fk_marks_entries_enrollment FOREIGN KEY (student_enrollment_id) REFERENCES student_enrollments (id)
) ENGINE = InnoDB;

CREATE TABLE report_cards (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id         BIGINT NOT NULL,
    exam_id                 BIGINT NOT NULL,
    student_enrollment_id   BIGINT NOT NULL,
    total_marks_obtained    DOUBLE NOT NULL,
    total_max_marks         DOUBLE NOT NULL,
    percentage              DOUBLE NOT NULL,
    overall_grade           VARCHAR(10) NOT NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    created_by              BIGINT,
    updated_by              BIGINT,
    CONSTRAINT uq_report_cards_exam_enrollment UNIQUE (exam_id, student_enrollment_id),
    CONSTRAINT fk_report_cards_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_report_cards_exam FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT fk_report_cards_enrollment FOREIGN KEY (student_enrollment_id) REFERENCES student_enrollments (id)
) ENGINE = InnoDB;
