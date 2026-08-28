-- Reporting module core schema (GitHub #186): SavedReport (name, description, SQL query,
-- DRAFT/PUBLISHED/ARCHIVED lifecycle) plus its filter-parameter definitions, column
-- display metadata, and per-report sharing list. No separate "owner" column - the
-- existing created_by audit column (already populated from TenantContext via
-- JpaConfig.auditorAware()) already answers "who owns this report."

CREATE TABLE saved_reports (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    name             VARCHAR(150) NOT NULL,
    description      VARCHAR(500),
    sql_query        TEXT NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT fk_saved_reports_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE INDEX idx_saved_reports_org_created_by ON saved_reports (organisation_id, created_by);

CREATE TABLE saved_report_parameters (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    saved_report_id  BIGINT NOT NULL,
    name             VARCHAR(100) NOT NULL,
    type             VARCHAR(20)  NOT NULL,
    label            VARCHAR(150) NOT NULL,
    sort_order       INT NOT NULL DEFAULT 0,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_saved_report_parameters_report_name UNIQUE (saved_report_id, name),
    CONSTRAINT fk_saved_report_parameters_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_saved_report_parameters_report FOREIGN KEY (saved_report_id) REFERENCES saved_reports (id)
) ENGINE = InnoDB;

CREATE INDEX idx_saved_report_parameters_report ON saved_report_parameters (saved_report_id);

CREATE TABLE saved_report_columns (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    saved_report_id  BIGINT NOT NULL,
    source_column    VARCHAR(100) NOT NULL,
    label            VARCHAR(150) NOT NULL,
    sort_order       INT NOT NULL DEFAULT 0,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_saved_report_columns_report_column UNIQUE (saved_report_id, source_column),
    CONSTRAINT fk_saved_report_columns_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_saved_report_columns_report FOREIGN KEY (saved_report_id) REFERENCES saved_reports (id)
) ENGINE = InnoDB;

CREATE INDEX idx_saved_report_columns_report ON saved_report_columns (saved_report_id);

-- Per-report ACL (a new pattern for this codebase - everything so far has used coarse
-- role-based @RequirePermission gating). principal_id is polymorphic (a User.id or a
-- Role.id depending on principal_type) with no FK constraint, same "reusable, not
-- narrowly-typed" shape as AuditLog's entity_type/entity_id. No separate "can_view" flag -
-- a report with no query result to view separately from running it makes a distinct
-- view-only grant meaningless, so can_run implies can_view and can_edit implies both.
CREATE TABLE saved_report_shares (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    saved_report_id  BIGINT NOT NULL,
    principal_type   VARCHAR(20)  NOT NULL,
    principal_id     BIGINT NOT NULL,
    can_run          BOOLEAN NOT NULL DEFAULT TRUE,
    can_edit         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_saved_report_shares_report_principal UNIQUE (saved_report_id, principal_type, principal_id),
    CONSTRAINT fk_saved_report_shares_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_saved_report_shares_report FOREIGN KEY (saved_report_id) REFERENCES saved_reports (id)
) ENGINE = InnoDB;

CREATE INDEX idx_saved_report_shares_report ON saved_report_shares (saved_report_id);
CREATE INDEX idx_saved_report_shares_principal ON saved_report_shares (principal_type, principal_id);
