-- Platform Settings page - a single-row settings table (not key-value: every setting so
-- far is a concrete, typed column, same "concrete columns over generic EAV" convention
-- as the rest of this schema). Starts with the one setting that already existed as a
-- static app.billing.trial-days property; extend with more columns as more settings get
-- a real UI, same "add a column when it's needed" call as everywhere else, not a
-- speculative generic store built ahead of the second setting actually showing up.
CREATE TABLE platform_settings (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    trial_days INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by BIGINT,
    updated_by BIGINT
) ENGINE = InnoDB;

-- Singleton row, fixed id=1 - PlatformSettingsRepository always reads/writes this one row.
INSERT INTO platform_settings (id, trial_days, created_at, updated_at)
VALUES (1, 14, NOW(6), NOW(6));
