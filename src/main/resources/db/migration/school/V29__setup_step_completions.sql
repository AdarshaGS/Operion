-- Which Structure Setup wizard steps (profile, business, campuses) a school has completed - the user
-- clicked Continue/Skip past them. Kept on the server per organisation so the wizard resumes at the same
-- place from any browser. step_key is validated by the app (SetupStep), not constrained here, so a new step
-- needs no migration.

CREATE TABLE setup_step_completions (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    step_key         VARCHAR(40) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_setup_step_completions_step UNIQUE (organisation_id, step_key)
) ENGINE = InnoDB;
