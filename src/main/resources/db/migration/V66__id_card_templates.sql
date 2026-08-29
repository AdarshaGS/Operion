-- ID Card Studio backend (#33). layout_json is a deliberate exception to the project's
-- usual avoid-JSON-columns convention - element positions/bindings are genuinely freeform.
CREATE TABLE id_card_templates (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    name              VARCHAR(255) NOT NULL,
    width_mm          DOUBLE NOT NULL,
    height_mm         DOUBLE NOT NULL,
    layout_json       TEXT NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_id_card_templates_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;
