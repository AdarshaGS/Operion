-- Per-organisation feature (module) switches, controlled by the platform owner only
-- (GitHub #198). Insert-only history, same convention as subscriptions: every toggle is a
-- new row and the latest row per (organisation, module) is the current state, so the table
-- doubles as the audit trail (created_by is the platform admin that flipped it).
-- No row for a module means it is enabled - existing organisations are unaffected, and a
-- module added to the catalogue later is on by default.

CREATE TABLE organisation_module_settings (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT       NOT NULL,
    module_code     VARCHAR(40)  NOT NULL,
    enabled         BOOLEAN      NOT NULL,
    note            VARCHAR(255),
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    created_by      BIGINT,
    updated_by      BIGINT,
    CONSTRAINT fk_org_module_settings_org FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE INDEX idx_org_module_settings_org ON organisation_module_settings (organisation_id, module_code, id);
