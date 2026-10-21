-- A scheduled announcement normally waits for the next working day when its time falls on a
-- holiday or weekly off. This flag lets one go out on the scheduled day anyway (e.g. a
-- "school is closed tomorrow" notice). Manual publish is never held back, so it ignores this.
ALTER TABLE announcements
    ADD COLUMN send_on_non_working_days BOOLEAN NOT NULL DEFAULT FALSE;
