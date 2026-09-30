-- #233: org-defined regulatory export schemas - a mapping from Operion's own fields to an external
-- reporting layout. Ships empty: no regional schema is baked into the core.

CREATE TABLE regulatory_export_schemas (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    code             VARCHAR(60) NOT NULL,
    name             VARCHAR(200) NOT NULL,
    description      VARCHAR(1000) NULL,
    entity_type      VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT fk_reg_export_schemas_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT uq_reg_export_schemas_code UNIQUE (organisation_id, code)
) ENGINE = InnoDB;

CREATE TABLE regulatory_export_fields (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    schema_id        BIGINT NOT NULL,
    ordinal          INT NOT NULL,
    column_header    VARCHAR(200) NOT NULL,
    source_field     VARCHAR(100) NOT NULL,
    default_value    VARCHAR(200) NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT fk_reg_export_fields_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_reg_export_fields_schema FOREIGN KEY (schema_id) REFERENCES regulatory_export_schemas (id),
    CONSTRAINT uq_reg_export_fields_ordinal UNIQUE (schema_id, ordinal)
) ENGINE = InnoDB;

INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('REGULATORY_EXPORT_MANAGE', 'organisation', 'Define regulatory export schemas, run exports and manage transfer connector jobs', NOW(6), NOW(6));
