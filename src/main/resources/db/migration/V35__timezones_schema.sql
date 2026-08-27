-- Global IANA timezone catalog - see Timezone.java. Closed and code-owned, same shape
-- as the permissions table (V1__foundation_schema.sql).

CREATE TABLE timezones (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64) NOT NULL,
    region      VARCHAR(32) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    created_by  BIGINT,
    updated_by  BIGINT,
    CONSTRAINT uq_timezones_name UNIQUE (name)
) ENGINE = InnoDB;
