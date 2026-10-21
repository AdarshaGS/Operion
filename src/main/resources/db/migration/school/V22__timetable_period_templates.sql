-- A school day per class group. Until now every class shared one list of periods; a template is a named
-- alternative (e.g. "Primary" with shorter periods) that some classes follow instead. Periods with no
-- template belong to the organisation's default day, so every existing period and class is untouched.
-- A class follows the default day unless a row in class_period_templates says otherwise.

CREATE TABLE period_templates (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    name             VARCHAR(100) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_period_templates_name UNIQUE (organisation_id, name)
) ENGINE = InnoDB;

ALTER TABLE periods ADD COLUMN template_id BIGINT NULL;
ALTER TABLE periods ADD CONSTRAINT fk_periods_template FOREIGN KEY (template_id) REFERENCES period_templates (id);

-- Ordinals stay unique within one day. The generated key maps the default day (NULL) to 0 so the
-- default day is protected too, which a plain unique key over a nullable column would not do.
ALTER TABLE periods ADD COLUMN template_key BIGINT GENERATED ALWAYS AS (COALESCE(template_id, 0)) STORED;
ALTER TABLE periods ADD CONSTRAINT uq_periods_org_template_ordinal UNIQUE (organisation_id, template_key, ordinal);
ALTER TABLE periods DROP INDEX uq_periods_org_ordinal;

CREATE TABLE class_period_templates (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    school_class_id  BIGINT NOT NULL,
    template_id      BIGINT NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_class_period_templates_class UNIQUE (organisation_id, school_class_id),
    CONSTRAINT fk_class_period_templates_class FOREIGN KEY (school_class_id) REFERENCES school_classes (id),
    CONSTRAINT fk_class_period_templates_template FOREIGN KEY (template_id) REFERENCES period_templates (id)
) ENGINE = InnoDB;
