-- Letter Formats - branded document templates (#31). At most one row per
-- (organisation, document_type); no backfill on provisioning - DocumentTemplateController
-- falls back to in-memory defaults on GET and creates the row lazily on first PUT.
CREATE TABLE document_templates (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    document_type     VARCHAR(30) NOT NULL,
    template_style    VARCHAR(20) NOT NULL,
    page_size         VARCHAR(20) NOT NULL,
    font_style        VARCHAR(50) NOT NULL,
    font_size         INT NOT NULL,
    header_subtext    VARCHAR(255),
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_document_templates_org_type UNIQUE (organisation_id, document_type),
    CONSTRAINT fk_document_templates_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;
