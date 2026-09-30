-- Platform CRM (milestone 32): leads, contacts, activities and follow-up tasks a platform
-- admin tracks before and after a school signs up. Not organisation-scoped tenant data -
-- same "visible to every platform admin, not Hibernate-tenant-filtered" convention as
-- support_tickets/plans (see V25's header comment). Contacts, activities and tasks link to
-- exactly one of a lead or an organisation, enforced by a check constraint.

CREATE TABLE crm_leads (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_name                 VARCHAR(255) NOT NULL,
    source                      VARCHAR(20) NOT NULL,
    stage                       VARCHAR(20) NOT NULL,
    estimated_student_count     INT,
    owner_id                    BIGINT,
    notes                       TEXT,
    converted_organisation_id   BIGINT,
    created_at                  DATETIME(6) NOT NULL,
    updated_at                  DATETIME(6) NOT NULL,
    created_by                  BIGINT,
    updated_by                  BIGINT,
    CONSTRAINT fk_crm_leads_owner FOREIGN KEY (owner_id) REFERENCES platform_admins (id),
    CONSTRAINT fk_crm_leads_converted_org FOREIGN KEY (converted_organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE INDEX idx_crm_leads_stage ON crm_leads (stage);

CREATE TABLE crm_contacts (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    lead_id             BIGINT,
    organisation_id     BIGINT,
    name                VARCHAR(255) NOT NULL,
    role_title          VARCHAR(255),
    email               VARCHAR(255),
    phone               VARCHAR(50),
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT fk_crm_contacts_lead FOREIGN KEY (lead_id) REFERENCES crm_leads (id),
    CONSTRAINT fk_crm_contacts_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT chk_crm_contacts_one_owner CHECK ((lead_id IS NOT NULL AND organisation_id IS NULL)
        OR (lead_id IS NULL AND organisation_id IS NOT NULL))
) ENGINE = InnoDB;

CREATE INDEX idx_crm_contacts_lead ON crm_contacts (lead_id);
CREATE INDEX idx_crm_contacts_organisation ON crm_contacts (organisation_id);

CREATE TABLE crm_activities (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    lead_id             BIGINT,
    organisation_id     BIGINT,
    type                VARCHAR(20) NOT NULL,
    body                TEXT NOT NULL,
    occurred_at         DATETIME(6) NOT NULL,
    author_id           BIGINT,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT fk_crm_activities_lead FOREIGN KEY (lead_id) REFERENCES crm_leads (id),
    CONSTRAINT fk_crm_activities_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_crm_activities_author FOREIGN KEY (author_id) REFERENCES platform_admins (id),
    CONSTRAINT chk_crm_activities_one_owner CHECK ((lead_id IS NOT NULL AND organisation_id IS NULL)
        OR (lead_id IS NULL AND organisation_id IS NOT NULL))
) ENGINE = InnoDB;

CREATE INDEX idx_crm_activities_lead ON crm_activities (lead_id);
CREATE INDEX idx_crm_activities_organisation ON crm_activities (organisation_id);

CREATE TABLE crm_tasks (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    lead_id             BIGINT,
    organisation_id     BIGINT,
    title               VARCHAR(255) NOT NULL,
    due_at              DATETIME(6) NOT NULL,
    assignee_id         BIGINT,
    completed_at        DATETIME(6),
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT fk_crm_tasks_lead FOREIGN KEY (lead_id) REFERENCES crm_leads (id),
    CONSTRAINT fk_crm_tasks_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_crm_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES platform_admins (id),
    CONSTRAINT chk_crm_tasks_one_owner CHECK ((lead_id IS NOT NULL AND organisation_id IS NULL)
        OR (lead_id IS NULL AND organisation_id IS NOT NULL))
) ENGINE = InnoDB;

CREATE INDEX idx_crm_tasks_lead ON crm_tasks (lead_id);
CREATE INDEX idx_crm_tasks_organisation ON crm_tasks (organisation_id);
CREATE INDEX idx_crm_tasks_due ON crm_tasks (completed_at, due_at);
