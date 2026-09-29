-- #177: high-value/serialized asset tracking, distinct from the bulk-stock ledger.
-- One row per physical unit (laptop, projector, furniture) - asset_tag is unique per
-- org, same convention as vehicles.registration_number/book_copies.accession_number.

CREATE TABLE assets (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id        BIGINT NOT NULL,
    item_id                BIGINT NOT NULL,
    campus_id              BIGINT NOT NULL,
    asset_tag              VARCHAR(100) NOT NULL,
    assigned_person_id     BIGINT,
    assigned_location      VARCHAR(255),
    purchase_date          DATE,
    warranty_expiry_date   DATE,
    status                 VARCHAR(20) NOT NULL,
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,
    created_by             BIGINT,
    updated_by             BIGINT,
    CONSTRAINT uq_assets_org_tag UNIQUE (organisation_id, asset_tag),
    CONSTRAINT fk_assets_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_assets_item FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT fk_assets_campus FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_assets_assigned_person FOREIGN KEY (assigned_person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_assets_campus ON assets (organisation_id, campus_id);

INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('INVENTORY_ASSET_MANAGE', 'inventory', 'Create and manage high-value asset records', NOW(6), NOW(6));
