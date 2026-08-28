-- Organisation Branding (GitHub #26) - assets/text that appear on every printed surface
-- (receipts, letterheads, ID cards, once those land). 1:1 with organisations, same shape
-- as organisation_configurations - keyed by the same id, no separate surrogate PK needed.
CREATE TABLE organisation_branding (
    organisation_id       BIGINT PRIMARY KEY,
    logo_ref              VARCHAR(255),
    stamp_ref             VARCHAR(255),
    signature_ref         VARCHAR(255),
    school_name_override  VARCHAR(255),
    address_line          VARCHAR(255),
    affiliation_text      VARCHAR(255),
    updated_at            DATETIME(6) NOT NULL,
    updated_by            BIGINT,
    CONSTRAINT fk_org_branding_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

-- Backfill: every org provisioned before this migration needs a row too, same reasoning
-- as organisation_configurations getting one per org at provisioning time - the
-- controller assumes the row always exists and 404s otherwise.
INSERT INTO organisation_branding (organisation_id, updated_at)
SELECT id, NOW(6) FROM organisations;
