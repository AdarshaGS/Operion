-- Draft/submit/approve workflow for marks entry (#134), mirroring class_attendance_registers
-- (V7). One row per exam_schedule, created lazily on first marks entry.

CREATE TABLE marks_entry_registers (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    exam_schedule_id  BIGINT NOT NULL,
    register_status   VARCHAR(20) NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_marks_entry_registers_schedule UNIQUE (exam_schedule_id),
    CONSTRAINT fk_marks_entry_registers_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_marks_entry_registers_schedule FOREIGN KEY (exam_schedule_id) REFERENCES exam_schedules (id)
) ENGINE = InnoDB;

-- Distinct, higher-trust permissions for the submit/approve transitions - same reasoning
-- as ATTENDANCE_LOCK/ATTENDANCE_UNLOCK (V81): being able to enter marks shouldn't
-- automatically grant the ability to submit or approve them.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('MARKS_SUBMIT',  'examination', 'Submit an entered marks register for review',   NOW(6), NOW(6)),
    ('MARKS_APPROVE', 'examination', 'Approve a submitted marks register for publish', NOW(6), NOW(6));
