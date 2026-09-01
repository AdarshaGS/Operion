-- Pass/fail (#135) and ranking (#136). Overall pass/fail strategy and the ranking toggle
-- are both org-level examination policy, kept together on examination_settings rather than
-- the generic organisation_configurations table (School-specific vocabulary boundary, same
-- as #75/#82).

CREATE TABLE examination_settings (
    id                            BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id               BIGINT NOT NULL,
    ranking_enabled               BOOLEAN NOT NULL DEFAULT FALSE,
    pass_fail_strategy            VARCHAR(30) NOT NULL DEFAULT 'PASS_EVERY_SUBJECT',
    minimum_aggregate_percentage  DOUBLE NOT NULL DEFAULT 33.0,
    created_at                    DATETIME(6)  NOT NULL,
    updated_at                    DATETIME(6)  NOT NULL,
    created_by                    BIGINT,
    updated_by                    BIGINT,
    CONSTRAINT uq_examination_settings_organisation UNIQUE (organisation_id),
    CONSTRAINT fk_examination_settings_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

ALTER TABLE report_cards
    ADD COLUMN passed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN class_rank INT NULL;

-- Not named "rank" - that's a reserved keyword since MySQL 8.0.2 (the RANK() window
-- function) and breaks Hibernate's unquoted generated INSERT/UPDATE statements.
ALTER TABLE marks_entries
    ADD COLUMN subject_rank INT NULL;
