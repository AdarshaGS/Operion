-- #235: prepared hand-off jobs for an external transfer system, one per student-exit TRANSFER. The
-- connector name is org-defined free text; the payload is built from a STUDENT regulatory export schema.

CREATE TABLE transfer_connector_jobs (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    student_exit_id     BIGINT NOT NULL,
    schema_id           BIGINT NOT NULL,
    connector_name      VARCHAR(100) NOT NULL,
    payload             LONGTEXT NOT NULL,
    status              VARCHAR(20) NOT NULL,
    external_reference  VARCHAR(200) NULL,
    failure_reason      VARCHAR(1000) NULL,
    prepared_by         BIGINT NULL,
    submitted_at        DATETIME(6) NULL,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT fk_transfer_jobs_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_transfer_jobs_exit FOREIGN KEY (student_exit_id) REFERENCES student_exits (id),
    CONSTRAINT fk_transfer_jobs_schema FOREIGN KEY (schema_id) REFERENCES regulatory_export_schemas (id)
) ENGINE = InnoDB;

CREATE INDEX idx_transfer_jobs_exit ON transfer_connector_jobs (organisation_id, student_exit_id);
CREATE INDEX idx_transfer_jobs_status ON transfer_connector_jobs (organisation_id, status);
