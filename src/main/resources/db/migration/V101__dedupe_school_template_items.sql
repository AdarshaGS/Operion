-- Cleans up duplicate rows created by a since-fixed race in
-- SchoolTemplateItemService.ensureSeeded(): the Settings panel fires one request per
-- category on load, and several could pass the "is this org's catalog empty" check before
-- the first one's seed insert committed, each re-seeding the full set. Keeps the
-- lowest-id row per (organisation_id, category, name) and removes the rest, then adds a
-- UNIQUE constraint so the same race can never reintroduce duplicates even if some other
-- path re-triggers it.
DELETE t1 FROM school_template_items t1
INNER JOIN school_template_items t2
    ON t1.organisation_id = t2.organisation_id
    AND t1.category = t2.category
    AND t1.name = t2.name
    AND t1.id > t2.id;

ALTER TABLE school_template_items
    ADD CONSTRAINT uq_school_template_items_org_category_name UNIQUE (organisation_id, category, name);
