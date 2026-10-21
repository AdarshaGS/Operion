-- Per-day overdue fine rate. 0 (the default) means late returns raise no automatic fine, so existing
-- organisations behave exactly as before until they set a rate.
ALTER TABLE library_settings ADD COLUMN fine_rate_per_day decimal(10,2) NOT NULL DEFAULT 0;
