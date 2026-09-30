-- Failed-login tracking moved out of process memory so lockout survives restarts and is
-- shared across instances. Global (no organisation_id), keyed by lower-cased email - same
-- reasoning as before: User is global, so keying by org would let an attacker dodge the
-- lockout by rotating org slugs.
CREATE TABLE login_attempts (
    email          VARCHAR(255) NOT NULL PRIMARY KEY,
    failed_count   INT          NOT NULL DEFAULT 0,
    locked_until   TIMESTAMP(6) NULL,
    updated_at     TIMESTAMP(6) NOT NULL
);
