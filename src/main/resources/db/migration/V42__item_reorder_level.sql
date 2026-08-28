-- Add reorder level to Item master data (GitHub #51) - nullable, no threshold set by
-- default. Low-stock reporting/auto-suggestion (Milestones 8/6) can build on this later;
-- this ticket only adds the field itself.
ALTER TABLE items
    ADD COLUMN reorder_level INT;
