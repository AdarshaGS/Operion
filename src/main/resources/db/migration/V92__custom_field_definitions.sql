-- Generic custom fields mechanism (#146): an org defines its own extra fields per record
-- type (e.g. Student's "Blood group", "Transport route") without a code change. This is
-- the admin-facing definitions table only - NOT an EAV values table: each opted-in
-- entity stores its own values directly in its own custom_fields JSON column (see the
-- students table alteration below), keyed by this table's field_key.
CREATE TABLE custom_field_definitions (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    entity_type      VARCHAR(50) NOT NULL,
    field_key        VARCHAR(100) NOT NULL,
    label            VARCHAR(150) NOT NULL,
    data_type        VARCHAR(20) NOT NULL,
    options          VARCHAR(500) NULL,
    required         BOOLEAN NOT NULL DEFAULT FALSE,
    status           VARCHAR(20) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT fk_custom_field_definitions_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT uq_custom_field_definitions_entity_key UNIQUE (organisation_id, entity_type, field_key)
) ENGINE = InnoDB;

CREATE INDEX idx_custom_field_definitions_organisation ON custom_field_definitions (organisation_id);
CREATE INDEX idx_custom_field_definitions_entity_type ON custom_field_definitions (entity_type);

-- Student is the first (and, for now, only) entity opted into custom fields - see #146's
-- own example. One JSON object per student, e.g. {"blood_group": "O+"}; a field with no
-- value stored is simply absent from the object rather than present with a null.
ALTER TABLE students ADD COLUMN custom_fields JSON NULL;
