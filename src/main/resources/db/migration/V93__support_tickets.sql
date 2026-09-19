-- Platform admin Support/Issues tracker (#277): a cross-org ticket, not scoped to any
-- one organisation's own tenant data - same "visible to every platform admin, not
-- Hibernate-tenant-filtered" convention as plans/subscriptions/platform_invoices (see
-- V25's header comment). MVP scope: single description field instead of a message
-- thread - see the ticket for why the full thread model is a separate future effort.

CREATE TABLE support_tickets (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id     BIGINT NOT NULL,
    subject             VARCHAR(255) NOT NULL,
    description         TEXT,
    status              VARCHAR(20) NOT NULL,
    priority            VARCHAR(20) NOT NULL,
    requester_email     VARCHAR(255) NOT NULL,
    assignee_email      VARCHAR(255),
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT fk_support_tickets_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE INDEX idx_support_tickets_status ON support_tickets (status);
