-- How many periods a week a subject gets in a class. NULL (the default) means no quota, so existing
-- classes keep working exactly as before; when set, the timetable will not place more than that many.
ALTER TABLE class_subjects ADD COLUMN weekly_periods INT NULL;
