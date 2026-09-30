-- #234: consent tracking and data-subject requests (DSR). Purposes are org-defined; nothing is seeded.

CREATE TABLE consent_purposes (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    code             VARCHAR(60) NOT NULL,
    name             VARCHAR(200) NOT NULL,
    description      VARCHAR(1000) NULL,
    status           VARCHAR(20) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT fk_consent_purposes_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT uq_consent_purposes_code UNIQUE (organisation_id, code)
) ENGINE = InnoDB;

-- Insert-only: a withdrawal is a new row, never an edit. The latest row per (person, purpose) is the current state.
CREATE TABLE consent_records (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    person_id        BIGINT NOT NULL,
    purpose_id       BIGINT NOT NULL,
    granted          BOOLEAN NOT NULL,
    source           VARCHAR(30) NOT NULL,
    note             VARCHAR(500) NULL,
    recorded_by      BIGINT NULL,
    recorded_at      DATETIME(6) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT fk_consent_records_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_consent_records_person FOREIGN KEY (person_id) REFERENCES persons (id),
    CONSTRAINT fk_consent_records_purpose FOREIGN KEY (purpose_id) REFERENCES consent_purposes (id)
) ENGINE = InnoDB;

CREATE INDEX idx_consent_records_person ON consent_records (organisation_id, person_id, purpose_id, recorded_at);

CREATE TABLE data_subject_requests (
    id                             BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id                BIGINT NOT NULL,
    person_id                      BIGINT NOT NULL,
    request_type                   VARCHAR(20) NOT NULL,
    status                         VARCHAR(30) NOT NULL,
    description                    VARCHAR(1000) NULL,
    received_date                  DATE NOT NULL,
    due_date                       DATE NOT NULL,
    identity_verified_by           BIGINT NULL,
    identity_verified_at           DATETIME(6) NULL,
    identity_verification_method   VARCHAR(200) NULL,
    resolved_by                    BIGINT NULL,
    resolved_at                    DATETIME(6) NULL,
    resolution_note                VARCHAR(1000) NULL,
    created_at                     DATETIME(6) NOT NULL,
    updated_at                     DATETIME(6) NOT NULL,
    created_by                     BIGINT,
    updated_by                     BIGINT,
    CONSTRAINT fk_dsr_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_dsr_person FOREIGN KEY (person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_dsr_status ON data_subject_requests (organisation_id, status, due_date);

INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('PRIVACY_MANAGE', 'organisation', 'Manage consent purposes and records, and handle data subject requests', NOW(6), NOW(6));
