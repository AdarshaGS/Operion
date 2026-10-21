-- Teacher-scoped attendance: plain ATTENDANCE_MARK now covers only the sections the
-- caller is the ACTIVE homeroom (class) teacher of. Marking any section in the org
-- needs this separate permission (Owners and ALL_FUNCTIONS still bypass).
INSERT IGNORE INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('ATTENDANCE_MARK_ANY_SECTION', 'attendance', 'Mark and submit daily attendance for any section, not only the sections you are class teacher of', NOW(6), NOW(6));

UPDATE permissions
SET description = 'Mark and submit daily attendance for sections you are the class teacher of'
WHERE code = 'ATTENDANCE_MARK';
