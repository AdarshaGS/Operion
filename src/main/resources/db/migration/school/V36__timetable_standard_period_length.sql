-- Standard class period length per organisation, so new class periods default to the same length all
-- day (breaks are added explicitly with their own times). NULL = not configured, so existing
-- organisations behave exactly as before.

ALTER TABLE timetable_settings ADD COLUMN period_minutes INT NULL;
