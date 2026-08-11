-- SaaS/billing module: PlatformAdmin (a separate, non-organisation-scoped login
-- identity), Plan, Subscription (insert-only per-org history, one ACTIVE at a time,
-- enforced in BillingService not the schema - same convention as is_current/
-- is_primary_guardian elsewhere), and PlatformInvoice (student-count snapshotted at
-- generation time, not a live-computed or separately-tracked Usage table - see
-- ai-context/load-context.md's SaaS/billing design notes for why that's deferred).
-- None of these tables carry organisation_id as a Hibernate @TenantId - they must be
-- visible across every organisation to a platform admin, so tenant filtering never
-- applies to them.

CREATE TABLE platform_admins (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(150) NOT NULL,
    email            VARCHAR(255) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_platform_admins_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE plans (
    id                             BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                           VARCHAR(30) NOT NULL,
    name                           VARCHAR(100) NOT NULL,
    price_per_student_per_year     DECIMAL(10, 2) NOT NULL,
    status                         VARCHAR(20) NOT NULL,
    created_at                     DATETIME(6) NOT NULL,
    updated_at                     DATETIME(6) NOT NULL,
    created_by                     BIGINT,
    updated_by                     BIGINT,
    CONSTRAINT uq_plans_code UNIQUE (code)
) ENGINE = InnoDB;

CREATE TABLE subscriptions (
    id                             BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id                BIGINT NOT NULL,
    plan_id                        BIGINT NOT NULL,
    price_per_student_per_year     DECIMAL(10, 2) NOT NULL,
    start_date                     DATE NOT NULL,
    end_date                       DATE,
    status                         VARCHAR(20) NOT NULL,
    created_at                     DATETIME(6) NOT NULL,
    updated_at                     DATETIME(6) NOT NULL,
    created_by                     BIGINT,
    updated_by                     BIGINT,
    CONSTRAINT fk_subscriptions_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES plans (id)
) ENGINE = InnoDB;

CREATE INDEX idx_subscriptions_org_status ON subscriptions (organisation_id, status);

CREATE TABLE platform_invoices (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id             BIGINT NOT NULL,
    subscription_id             BIGINT NOT NULL,
    period_start                DATE NOT NULL,
    period_end                  DATE NOT NULL,
    student_count_at_billing    INT NOT NULL,
    amount                      DECIMAL(12, 2) NOT NULL,
    status                      VARCHAR(20) NOT NULL,
    issued_at                   DATETIME(6) NOT NULL,
    due_date                    DATE NOT NULL,
    paid_at                     DATETIME(6),
    created_at                  DATETIME(6) NOT NULL,
    updated_at                  DATETIME(6) NOT NULL,
    created_by                  BIGINT,
    updated_by                  BIGINT,
    CONSTRAINT fk_platform_invoices_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_platform_invoices_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
) ENGINE = InnoDB;

CREATE INDEX idx_platform_invoices_org_status ON platform_invoices (organisation_id, status);

-- Dev-only bootstrap platform admin - there is no self-registration endpoint for this
-- identity plane (unlike Organisation, which is deliberately public for tenant
-- onboarding; a platform admin is you, not a customer). email: admin@operion.platform,
-- password: ChangeMe123! - MUST be rotated before any real deployment.
INSERT INTO platform_admins (name, email, password_hash, status, created_at, updated_at)
VALUES ('Platform Admin', 'admin@operion.platform', '$2y$10$22VZbJawIP7B8ySzK6kwdOFxwdGssj/LQx9MTA.S2.x76/Qf1JC/u', 'ACTIVE', NOW(6), NOW(6));
