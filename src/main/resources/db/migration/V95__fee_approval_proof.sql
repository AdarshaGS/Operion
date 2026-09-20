ALTER TABLE waivers
    ADD COLUMN proof_file_reference VARCHAR(255) NULL,
    ADD COLUMN proof_file_name      VARCHAR(255) NULL;

ALTER TABLE refunds
    ADD COLUMN proof_file_reference VARCHAR(255) NULL,
    ADD COLUMN proof_file_name      VARCHAR(255) NULL;

ALTER TABLE adjustments
    ADD COLUMN proof_file_reference VARCHAR(255) NULL,
    ADD COLUMN proof_file_name      VARCHAR(255) NULL;
