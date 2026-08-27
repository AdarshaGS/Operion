CREATE TABLE academic_configurations (
    organisation_id    BIGINT PRIMARY KEY,
    school_start_time  TIME,
    school_end_time    TIME,
    updated_at         DATETIME(6) NOT NULL,
    updated_by         BIGINT,
    CONSTRAINT fk_academic_configurations_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

INSERT INTO academic_configurations (organisation_id, school_start_time, school_end_time, updated_at)
SELECT organisation_id, school_start_time, school_end_time, NOW()
FROM organisation_configurations
WHERE school_start_time IS NOT NULL OR school_end_time IS NOT NULL;

ALTER TABLE organisation_configurations
    DROP COLUMN school_start_time,
    DROP COLUMN school_end_time;
