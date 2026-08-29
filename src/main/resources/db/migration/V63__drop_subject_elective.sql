-- #109: elective/mandatory was tracked twice (Subject.is_elective at the catalog level,
-- class_subjects.mandatory per class-assignment) with nothing reconciling them. The
-- per-class value is the only one ever read/enforced - drop the unused, contradictable
-- catalog-level flag rather than keep two facts that can disagree.
ALTER TABLE subjects DROP COLUMN is_elective;
