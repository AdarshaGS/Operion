-- The actual security boundary for user-authored report SQL (GitHub #187): a dedicated,
-- low-privilege MySQL user with SELECT-only on the `reporting` schema (V54) and no grant
-- of any kind on the application's real schema. ReportExecutionService connects as this
-- user (app.reporting.datasource.*), so even a bug in its app-level SQL validation cannot
-- reach real tables or perform a write - the DB user is physically unable to.
--
-- Dev-only seeded password, same "rotate before any real deployment" trade-off already
-- accepted for the platform-admin seed (admin@operion.platform / ChangeMe123!, see
-- ai-context/load-context.md). Unlike that seed, this one can't be rotated by simply
-- updating a row - it requires a manual `ALTER USER 'reporting_ro'@'%' IDENTIFIED BY ...`
-- (plus updating REPORTING_DB_PASSWORD) since Flyway migrations don't re-run.
--
-- Requires the app's own migration-running DB user to hold CREATE USER/GRANT privilege
-- (true for local dev root, not guaranteed on a locked-down managed DB - flag before
-- deploying against one).
CREATE USER IF NOT EXISTS 'reporting_ro'@'%' IDENTIFIED BY 'changeme-reporting-ro';
GRANT SELECT ON reporting.* TO 'reporting_ro'@'%';
FLUSH PRIVILEGES;
