CREATE TABLE departments (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    name               VARCHAR(255) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT fk_departments_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE INDEX idx_departments_org_status ON departments (organisation_id, status);

CREATE TABLE designations (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    name               VARCHAR(255) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT fk_designations_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE INDEX idx_designations_org_status ON designations (organisation_id, status);
