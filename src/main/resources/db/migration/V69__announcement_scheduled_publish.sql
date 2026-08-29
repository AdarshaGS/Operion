-- Backs Announcement.scheduledAt: an optional future publish time, auto-published by
-- ScheduledAnnouncementPublisher once it's in the past, instead of only a manual publish.

ALTER TABLE announcements ADD COLUMN scheduled_at DATETIME(6);

CREATE INDEX idx_announcements_status_scheduled_at ON announcements (organisation_id, status, scheduled_at);
