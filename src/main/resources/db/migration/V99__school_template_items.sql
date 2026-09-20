-- Per-organisation, editable "School setup template" catalog (see com.operion.onboarding).
-- Replaces a hardcoded Java defaults list: every row here is an admin-managed draft item
-- that SchoolTemplateService.apply() turns into a real Department/Designation/Role/
-- GradeLevel/FeeCategory/ItemCategory/GradingScaleBand row. A fresh org has zero rows here
-- until first accessed, at which point SchoolTemplateItemService bootstraps it once from
-- SchoolTemplateBootstrapDefaults - from then on it's pure CRUD, no code change needed to
-- add/remove a default.
CREATE TABLE school_template_items (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    category          VARCHAR(20) NOT NULL,
    name              VARCHAR(150) NOT NULL,
    code              VARCHAR(50),
    description       VARCHAR(500),
    sequence_order    INT,
    stage             VARCHAR(50),
    category_type     VARCHAR(20),
    min_percentage    DOUBLE,
    remark            VARCHAR(255),
    permission_codes  VARCHAR(2000),
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_school_template_items_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE INDEX idx_school_template_items_org_category ON school_template_items (organisation_id, category);
