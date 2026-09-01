-- Generic store for 3rd-party integration credentials, configurable from the
-- platform-admin UI without a redeploy - see com.operion.integration. One row per
-- provider account (platform-level, not per-organisation - same "one shared account"
-- model already used for Razorpay, see RazorpayCredentialsProvider), with a flexible
-- key/value property list underneath rather than fixed columns, so a future provider
-- doesn't need its own migration.

-- id/created_at/updated_at/created_by/updated_by mirror BaseEntity's shape (see
-- PlatformAdmin) - both entities extend it, no per-org tenant_id since these are
-- platform-level (not TenantScopedEntity, same choice as PlatformAdmin/Organisation).
CREATE TABLE external_services (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_key VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_by BIGINT NULL
);

-- property_value is encrypted (ExternalServiceSecretCipher) whenever is_secret is TRUE;
-- plain text otherwise (sender names/addresses aren't secrets).
CREATE TABLE external_service_properties (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    external_service_id BIGINT NOT NULL,
    property_key VARCHAR(100) NOT NULL,
    property_value TEXT NULL,
    is_secret BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT fk_external_service_properties_service FOREIGN KEY (external_service_id) REFERENCES external_services (id),
    CONSTRAINT uq_external_service_properties UNIQUE (external_service_id, property_key)
);

INSERT INTO external_services (service_key, display_name) VALUES ('brevo', 'Brevo (Email & SMS)');

INSERT INTO external_service_properties (external_service_id, property_key, property_value, is_secret)
SELECT id, 'email.api-key', NULL, TRUE FROM external_services WHERE service_key = 'brevo';
INSERT INTO external_service_properties (external_service_id, property_key, property_value, is_secret)
SELECT id, 'email.sender-email', NULL, FALSE FROM external_services WHERE service_key = 'brevo';
INSERT INTO external_service_properties (external_service_id, property_key, property_value, is_secret)
SELECT id, 'email.sender-name', 'Operion', FALSE FROM external_services WHERE service_key = 'brevo';
INSERT INTO external_service_properties (external_service_id, property_key, property_value, is_secret)
SELECT id, 'sms.api-key', NULL, TRUE FROM external_services WHERE service_key = 'brevo';
INSERT INTO external_service_properties (external_service_id, property_key, property_value, is_secret)
SELECT id, 'sms.sender', NULL, FALSE FROM external_services WHERE service_key = 'brevo';
