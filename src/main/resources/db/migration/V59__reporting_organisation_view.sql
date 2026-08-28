-- `organisations` itself was deliberately left out of V58's curated views: it's the
-- tenant root and has no organisation_id column of its own to filter by, so a plain
-- SELECT grant on it (or a view mirroring it row-for-row) would let any report author in
-- any school read every other school's name/slug/status on the platform. This view
-- exposes exactly one row - the caller's own organisation - by filtering on id itself
-- rather than an organisation_id column, same reporting_current_org_id() mechanism as
-- every other reporting_* view.
CREATE VIEW reporting_organisation AS
SELECT
    o.id                 AS organisation_id,
    o.name               AS name,
    o.legal_name         AS legal_name,
    o.slug               AS slug,
    o.status             AS status,
    oc.timezone          AS timezone,
    oc.default_currency  AS default_currency,
    oc.date_format       AS date_format,
    oc.working_days_mask AS working_days_mask
FROM organisations o
LEFT JOIN organisation_configurations oc ON oc.organisation_id = o.id
WHERE o.id = reporting_current_org_id();

GRANT SELECT ON reporting_organisation TO 'reporting_ro'@'%';
FLUSH PRIVILEGES;
