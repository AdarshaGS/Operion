-- Parent portal identity: PortalInvite lets a Guardian claim a real login (User +
-- OrganisationMembership on a "Guardian" role) without a parallel auth system - see
-- ai-context/mobile-app-context.md for the design brief this closes the first blocker
-- from. token_hash is bcrypt, same PasswordEncoder as User.password_hash; the raw token
-- only ever exists in the grant-portal-access API response, never persisted.

CREATE TABLE portal_invites (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id    BIGINT NOT NULL,
    person_id          BIGINT NOT NULL,
    token_hash         VARCHAR(255) NOT NULL,
    expires_at         DATETIME(6) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT fk_portal_invites_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_portal_invites_person FOREIGN KEY (person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_portal_invites_org_status ON portal_invites (organisation_id, status);
