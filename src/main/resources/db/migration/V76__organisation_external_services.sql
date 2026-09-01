-- Reworks V72's external-service credential store from a single platform-shared account
-- into per-organisation BYOK: a platform admin grants an organisation access to an
-- integration (organisation_external_services.enabled), and only that organisation's own
-- admin ever supplies the actual credential values (organisation_external_service_properties)
-- - see com.operion.integration for the reasoning. No org had real credentials in
-- external_service_properties yet, so it's dropped outright rather than migrated.

DROP TABLE external_service_properties;
ALTER TABLE external_services DROP COLUMN enabled;

-- Plain organisation_id FK, not a Hibernate @TenantId - a platform admin must see and
-- toggle this across every organisation, the same "visible across every org" reasoning
-- as subscriptions (V25).
CREATE TABLE organisation_external_services (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id      BIGINT NOT NULL,
    external_service_id  BIGINT NOT NULL,
    enabled              BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by           BIGINT NULL,
    updated_by           BIGINT NULL,
    CONSTRAINT fk_org_ext_services_org FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_org_ext_services_service FOREIGN KEY (external_service_id) REFERENCES external_services (id),
    CONSTRAINT uq_org_ext_services UNIQUE (organisation_id, external_service_id)
);

-- @TenantId-scoped (organisation_id auto-populated/auto-filtered by Hibernate from
-- TenantContext) - the opposite visibility rule from the table above, deliberately: a
-- platform-admin-authenticated query never carries an organisation TenantContext, so it
-- structurally cannot see any organisation's stored credential values, not just by
-- access-control convention.
CREATE TABLE organisation_external_service_properties (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id      BIGINT NOT NULL,
    external_service_id  BIGINT NOT NULL,
    property_key         VARCHAR(100) NOT NULL,
    property_value       TEXT NULL,
    is_secret            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by           BIGINT NULL,
    updated_by           BIGINT NULL,
    CONSTRAINT fk_org_ext_service_props_org FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_org_ext_service_props_service FOREIGN KEY (external_service_id) REFERENCES external_services (id),
    CONSTRAINT uq_org_ext_service_props UNIQUE (organisation_id, external_service_id, property_key)
);
