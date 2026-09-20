-- Import history (Imports & exports migration center): one summary row per completed
-- bulk import run, written by ImportRunService after a real (non-validate-only) import.
-- No per-row detail persisted - that's only shown live in the upload dialog for that
-- session; this table only backs the "View import history" list.
CREATE TABLE import_runs (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT       NOT NULL,
    entity_type       VARCHAR(100) NOT NULL,
    file_name         VARCHAR(255) NOT NULL,
    total_rows        INT          NOT NULL,
    imported_count    INT          NOT NULL,
    error_count       INT          NOT NULL,
    duplicate_count   INT          NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_import_runs_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE INDEX idx_import_runs_organisation ON import_runs (organisation_id);
