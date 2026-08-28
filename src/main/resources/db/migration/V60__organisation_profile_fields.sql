-- Organisation Profile settings (GitHub #141) - primary contact, address, and tax/legal
-- identifier, alongside the existing rarely-changing settings on organisation_configurations.
ALTER TABLE organisation_configurations
    ADD COLUMN primary_contact_name  VARCHAR(200),
    ADD COLUMN primary_contact_email VARCHAR(255),
    ADD COLUMN primary_contact_phone VARCHAR(30),
    ADD COLUMN address_line1         VARCHAR(255),
    ADD COLUMN address_line2         VARCHAR(255),
    ADD COLUMN city                  VARCHAR(100),
    ADD COLUMN state                 VARCHAR(100),
    ADD COLUMN pincode               VARCHAR(20),
    ADD COLUMN tax_identifier        VARCHAR(100);
