-- Timetable policy per organisation (all optional: 0 / NULL means "no rule", so existing
-- organisations behave exactly as before until they set something) and a slot type on periods so
-- a whole-school activity (assembly, games) can be told apart from a break.

CREATE TABLE timetable_settings (
    id                              BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id                 BIGINT NOT NULL,
    gap_minutes                     INT NOT NULL DEFAULT 0,
    max_teacher_periods_per_day     INT NULL,
    max_teacher_periods_per_week    INT NULL,
    created_at                      DATETIME(6) NOT NULL,
    updated_at                      DATETIME(6) NOT NULL,
    created_by                      BIGINT,
    updated_by                      BIGINT,
    CONSTRAINT uq_timetable_settings_organisation UNIQUE (organisation_id)
) ENGINE = InnoDB;

-- TEACHING holds subject entries; BREAK and SPECIAL never do. is_break stays as "not a teaching slot".
ALTER TABLE periods ADD COLUMN slot_type VARCHAR(20) NOT NULL DEFAULT 'TEACHING';
UPDATE periods SET slot_type = 'BREAK' WHERE is_break = TRUE;
