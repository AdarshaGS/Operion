-- Reporting module permissions (GitHub #186). REPORT_CREATE lets any granted org member
-- author their own reports; REPORT_MANAGE is the admin-sees-everything override (view/edit/
-- archive any report in the org, not just owned/shared ones); REPORT_EXPORT is a distinct
-- gate on top of run access, matching the spec's explicit call-out of export as its own
-- concern. Running/editing a specific report you own or were shared is authorized
-- per-record in SavedReportService, not through this coarse catalog.
INSERT INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('REPORT_CREATE', 'reporting', 'Create and own saved reports', NOW(6), NOW(6)),
    ('REPORT_MANAGE', 'reporting', 'View, edit, and archive any saved report in the organisation', NOW(6), NOW(6)),
    ('REPORT_EXPORT', 'reporting', 'Export report results', NOW(6), NOW(6));
