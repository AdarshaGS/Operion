-- Separate, higher-trust permission for reversing a LOCKED register - being able to
-- lock a register (ATTENDANCE_LOCK) shouldn't automatically grant the ability to
-- unlock one, per issue #119.
INSERT IGNORE INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('ATTENDANCE_UNLOCK', 'attendance', 'Unlock a LOCKED class attendance register so it can be corrected again', NOW(6), NOW(6));
