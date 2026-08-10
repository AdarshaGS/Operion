-- Transportation module permissions, following the same closed/code-owned catalog
-- convention as V2/V4/V6/V8/V10/V12/V14. No enforcement wired yet (none exists anywhere
-- in the codebase yet) - these rows exist so roles can be configured against them once
-- enforcement is built.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('TRANSPORT_VEHICLE_MANAGE',     'transport', 'Create and manage vehicles',                       NOW(6), NOW(6)),
    ('TRANSPORT_ROUTE_MANAGE',       'transport', 'Create and manage routes and stops',                NOW(6), NOW(6)),
    ('TRANSPORT_ASSIGNMENT_MANAGE',  'transport', 'Assign, reassign, and end student transport assignments', NOW(6), NOW(6)),
    ('TRANSPORT_TRIP_LOG',           'transport', 'Start, complete, and cancel trip logs',             NOW(6), NOW(6)),
    ('TRANSPORT_VIEW',               'transport', 'View vehicles, routes, assignments, and trip logs', NOW(6), NOW(6));
