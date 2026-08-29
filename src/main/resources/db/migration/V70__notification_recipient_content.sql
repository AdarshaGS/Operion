-- Backs real EMAIL/SMS dispatch (see NotificationDispatchService): the content sent for a
-- notification is now captured on the recipient row itself at fan-out time, so the
-- worker that later dispatches PENDING rows doesn't need to reload the source
-- Announcement/NotificationTemplate to know what to send.

ALTER TABLE notification_recipients ADD COLUMN subject VARCHAR(200);
ALTER TABLE notification_recipients ADD COLUMN body TEXT;

-- Backfill existing rows (all IN_APP, already SENT) with an empty body so the new
-- NOT NULL constraint can be applied without breaking them.
UPDATE notification_recipients SET body = '' WHERE body IS NULL;
ALTER TABLE notification_recipients MODIFY COLUMN body TEXT NOT NULL;

-- idx_notification_recipients_status (V13) already covers the (organisation_id,
-- delivery_status) lookup NotificationDispatchWorker needs for PENDING rows.
