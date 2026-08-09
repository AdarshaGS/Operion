-- Attendance module permissions, following the same closed/code-owned catalog
-- convention as V2/V4/V6. No enforcement wired yet (none exists anywhere in the
-- codebase yet) - these rows exist so roles can be configured against them once
-- enforcement is built.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('ATTENDANCE_MARK',       'attendance', 'Mark daily student attendance for a section', NOW(6), NOW(6)),
    ('ATTENDANCE_VIEW',       'attendance', 'View student attendance records',             NOW(6), NOW(6)),
    ('ATTENDANCE_CORRECT',    'attendance', 'Correct an already-marked attendance record', NOW(6), NOW(6)),
    ('ATTENDANCE_LOCK',       'attendance', 'Submit and lock a class attendance register', NOW(6), NOW(6)),
    ('STAFF_ATTENDANCE_MARK', 'attendance', 'Record staff check-in/check-out',             NOW(6), NOW(6)),
    ('STAFF_ATTENDANCE_VIEW', 'attendance', 'View staff attendance records',               NOW(6), NOW(6));
