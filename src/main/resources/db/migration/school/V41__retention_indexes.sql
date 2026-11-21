-- RetentionScheduler's purge selects an organisation's SENT/FAILED outbox rows and terminal-status notification
-- recipients older than a cutoff, a page at a time. The existing (organisation_id, status) indexes would walk every
-- row of a status to apply the age filter; adding created_at makes it a range scan. login_attempts needs nothing:
-- it holds one row per (organisation, email) and already has idx_login_attempts_org.
CREATE INDEX `idx_email_outbox_org_status_created` ON `email_outbox` (`organisation_id`, `status`, `created_at`);
CREATE INDEX `idx_notification_recipients_status_created` ON `notification_recipients` (`organisation_id`, `delivery_status`, `created_at`);
