-- Milestone 14 "Organisation Provisioning Enhancements" (#84, #85). Primary contact and
-- address already live on organisation_configurations (see V60__organisation_profile_fields.sql,
-- GitHub #141) - only country is genuinely new there. organisation_type/board/school_code
-- have no existing home, so they land on organisations alongside name/legalName/slug.
ALTER TABLE organisations
    ADD COLUMN organisation_type VARCHAR(20) NOT NULL DEFAULT 'SCHOOL',
    ADD COLUMN board             VARCHAR(20),
    ADD COLUMN school_code       VARCHAR(50),
    ADD CONSTRAINT uq_organisations_school_code UNIQUE (school_code);

ALTER TABLE organisation_configurations
    ADD COLUMN country VARCHAR(100);
