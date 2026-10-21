-- Teacher performance (KPI) scorecard. An organisation picks which built-in metrics to track, how much each
-- weighs and the target it is scored against; nothing is enabled until it opts in. Scores are computed on read
-- from existing records (staff attendance, lesson plans), so there is no snapshot table to go stale.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('KPI_VIEW', 'performance', 'View the teacher performance scorecard', NOW(6), NOW(6)),
    ('KPI_MANAGE', 'performance', 'Choose which performance metrics are tracked, and their weights and targets', NOW(6), NOW(6));

CREATE TABLE kpi_definitions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    metric          VARCHAR(50) NOT NULL,
    enabled         TINYINT(1) NOT NULL DEFAULT 0,
    weight          INT NOT NULL DEFAULT 1,
    target          DECIMAL(5,2) NOT NULL DEFAULT 100,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    created_by      BIGINT,
    updated_by      BIGINT,
    CONSTRAINT uq_kpi_definitions_org_metric UNIQUE (organisation_id, metric)
) ENGINE = InnoDB;
