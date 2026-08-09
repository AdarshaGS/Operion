-- Foundation module schema: Organisation, Campus, AcademicYear, Configuration,
-- User, Person, Role, Permission, OrganisationMembership, AuditLog.
-- See ai-context/erp-system-plan.md §1 for the design this migration implements.

CREATE TABLE organisations (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    legal_name    VARCHAR(255),
    slug          VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    created_by    BIGINT,
    updated_by    BIGINT,
    CONSTRAINT uq_organisations_slug UNIQUE (slug)
) ENGINE = InnoDB;

CREATE TABLE organisation_configurations (
    organisation_id    BIGINT PRIMARY KEY,
    timezone           VARCHAR(64),
    default_currency   VARCHAR(8),
    date_format        VARCHAR(32),
    working_days_mask  INT NOT NULL,
    school_start_time  TIME,
    school_end_time    TIME,
    logo_url           VARCHAR(512),
    primary_color      VARCHAR(16),
    updated_at         DATETIME(6) NOT NULL,
    updated_by         BIGINT,
    CONSTRAINT fk_org_config_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE TABLE campuses (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    name             VARCHAR(255) NOT NULL,
    code             VARCHAR(50)  NOT NULL,
    address_line1    VARCHAR(255),
    address_line2    VARCHAR(255),
    city             VARCHAR(100),
    state            VARCHAR(100),
    pincode          VARCHAR(20),
    timezone         VARCHAR(64),
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_campuses_org_code UNIQUE (organisation_id, code),
    CONSTRAINT fk_campuses_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE INDEX idx_campuses_organisation ON campuses (organisation_id);

CREATE TABLE academic_years (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    name             VARCHAR(50) NOT NULL,
    start_date       DATE NOT NULL,
    end_date         DATE NOT NULL,
    is_current       BOOLEAN NOT NULL DEFAULT FALSE,
    status           VARCHAR(20) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_academic_years_org_name UNIQUE (organisation_id, name),
    CONSTRAINT fk_academic_years_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE INDEX idx_academic_years_org_current ON academic_years (organisation_id, is_current);

CREATE TABLE users (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    phone          VARCHAR(20),
    password_hash  VARCHAR(255) NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    last_login_at  DATETIME(6),
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    created_by     BIGINT,
    updated_by     BIGINT,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_phone UNIQUE (phone)
) ENGINE = InnoDB;

CREATE TABLE persons (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    user_id          BIGINT,
    first_name       VARCHAR(100) NOT NULL,
    last_name        VARCHAR(100),
    date_of_birth    DATE,
    gender           VARCHAR(20),
    phone            VARCHAR(20),
    email            VARCHAR(255),
    photo_url        VARCHAR(512),
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_persons_org_user UNIQUE (organisation_id, user_id),
    CONSTRAINT fk_persons_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_persons_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE INDEX idx_persons_organisation ON persons (organisation_id);

CREATE TABLE roles (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    name               VARCHAR(100) NOT NULL,
    description        VARCHAR(255),
    is_system_default  BOOLEAN NOT NULL DEFAULT FALSE,
    status             VARCHAR(20)  NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT uq_roles_org_name UNIQUE (organisation_id, name),
    CONSTRAINT fk_roles_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE = InnoDB;

CREATE TABLE permissions (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(100) NOT NULL,
    module       VARCHAR(50)  NOT NULL,
    description  VARCHAR(255),
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    created_by   BIGINT,
    updated_by   BIGINT,
    CONSTRAINT uq_permissions_code UNIQUE (code)
) ENGINE = InnoDB;

CREATE TABLE role_permissions (
    role_id        BIGINT NOT NULL,
    permission_id  BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
) ENGINE = InnoDB;

CREATE TABLE organisation_memberships (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    user_id          BIGINT NOT NULL,
    person_id        BIGINT NOT NULL,
    role_id          BIGINT NOT NULL,
    campus_id        BIGINT,
    status           VARCHAR(20) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_memberships_org_user_role_campus UNIQUE (organisation_id, user_id, role_id, campus_id),
    CONSTRAINT fk_memberships_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_memberships_person FOREIGN KEY (person_id) REFERENCES persons (id),
    CONSTRAINT fk_memberships_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_memberships_campus FOREIGN KEY (campus_id) REFERENCES campuses (id)
) ENGINE = InnoDB;

CREATE INDEX idx_memberships_org_user ON organisation_memberships (organisation_id, user_id);
CREATE INDEX idx_memberships_user ON organisation_memberships (user_id);

CREATE TABLE audit_logs (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT,
    actor_user_id    BIGINT,
    entity_type      VARCHAR(100) NOT NULL,
    entity_id        BIGINT NOT NULL,
    action           VARCHAR(50)  NOT NULL,
    before_value     JSON,
    after_value      JSON,
    occurred_at      DATETIME(6)  NOT NULL,
    ip_address       VARCHAR(64)
) ENGINE = InnoDB;

CREATE INDEX idx_audit_logs_org_entity ON audit_logs (organisation_id, entity_type, entity_id);
CREATE INDEX idx_audit_logs_org_occurred ON audit_logs (organisation_id, occurred_at);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_user_id);
