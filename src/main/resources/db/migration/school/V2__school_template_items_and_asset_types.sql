-- Default inventory items and asset types for the School Starter Pack: two new template
-- categories, ITEM (stock-tracked consumables) and ASSET_TYPE (catalog items a school registers
-- individual Asset units against). Both become plain inventory Items on apply - no stock or Asset
-- units are ever seeded, since a unit needs a campus and a tag only the school can supply.

ALTER TABLE school_template_master_items
  ADD COLUMN unit varchar(20) DEFAULT NULL,
  ADD COLUMN item_category_code varchar(50) DEFAULT NULL;

-- included = false marks a row "not required": kept in the catalog, ignored by preview and apply.
ALTER TABLE school_template_items
  ADD COLUMN unit varchar(20) DEFAULT NULL,
  ADD COLUMN item_category_code varchar(50) DEFAULT NULL,
  ADD COLUMN included tinyint(1) NOT NULL DEFAULT 1;

INSERT INTO school_template_master_items
  (category, name, code, unit, item_category_code, module_code, created_at, updated_at)
VALUES
('ITEM', 'Notebook', 'NOTEBOOK', 'PCS', 'STATIONERY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Ruled Register', 'REGISTER_RULED', 'PCS', 'STATIONERY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Ballpoint Pen', 'PEN_BALLPOINT', 'PCS', 'STATIONERY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Pencil', 'PENCIL', 'PCS', 'STATIONERY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Eraser', 'ERASER', 'PCS', 'STATIONERY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Whiteboard Marker', 'MARKER_WHITEBOARD', 'PCS', 'STATIONERY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Chalk Box', 'CHALK_BOX', 'BOX', 'STATIONERY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'A4 Paper Ream', 'PAPER_A4_REAM', 'REAM', 'STATIONERY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Stapler Pins', 'STAPLER_PINS', 'BOX', 'STATIONERY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'File Folder', 'FILE_FOLDER', 'PCS', 'STATIONERY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Glue Stick', 'GLUE_STICK', 'PCS', 'STATIONERY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Chart Paper', 'CHART_PAPER', 'PCS', 'STATIONERY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Floor Cleaner', 'FLOOR_CLEANER', 'LTR', 'CLEANING_SUPPLIES', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Hand Wash', 'HAND_WASH', 'LTR', 'CLEANING_SUPPLIES', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Toilet Cleaner', 'TOILET_CLEANER', 'LTR', 'CLEANING_SUPPLIES', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Disinfectant', 'DISINFECTANT', 'LTR', 'CLEANING_SUPPLIES', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Broom', 'BROOM', 'PCS', 'CLEANING_SUPPLIES', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Mop', 'MOP', 'PCS', 'CLEANING_SUPPLIES', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Dustbin Liner', 'DUSTBIN_LINER', 'PACK', 'CLEANING_SUPPLIES', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Tissue Paper', 'TISSUE_PAPER', 'PACK', 'CLEANING_SUPPLIES', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Football', 'FOOTBALL', 'PCS', 'SPORTS', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Volleyball', 'VOLLEYBALL', 'PCS', 'SPORTS', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Skipping Rope', 'SKIPPING_ROPE', 'PCS', 'SPORTS', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Badminton Shuttlecock', 'SHUTTLECOCK', 'BOX', 'SPORTS', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Test Tube', 'TEST_TUBE', 'PCS', 'LABORATORY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Beaker', 'BEAKER', 'PCS', 'LABORATORY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Litmus Paper', 'LITMUS_PAPER', 'PACK', 'LABORATORY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Disposable Gloves', 'GLOVES_DISPOSABLE', 'BOX', 'LABORATORY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Safety Goggles', 'GOGGLES_SAFETY', 'PCS', 'LABORATORY', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Printer Toner Cartridge', 'TONER_CARTRIDGE', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Batteries (AA)', 'BATTERY_AA', 'PACK', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Extension Cord', 'EXTENSION_CORD', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'LED Bulb', 'LED_BULB', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Bandage Roll', 'BANDAGE_ROLL', 'PCS', 'GENERAL', 'INVENTORY', NOW(6), NOW(6)),
('ITEM', 'Adhesive Tape', 'ADHESIVE_TAPE', 'PCS', 'GENERAL', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Student Desk', 'DESK_STUDENT', 'PCS', 'FURNITURE', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Student Chair', 'CHAIR_STUDENT', 'PCS', 'FURNITURE', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Teacher Table', 'TABLE_TEACHER', 'PCS', 'FURNITURE', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Teacher Chair', 'CHAIR_TEACHER', 'PCS', 'FURNITURE', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Storage Cabinet', 'CABINET_STORAGE', 'PCS', 'FURNITURE', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Bookshelf', 'BOOKSHELF', 'PCS', 'FURNITURE', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Notice Board', 'NOTICE_BOARD', 'PCS', 'FURNITURE', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Whiteboard', 'WHITEBOARD', 'PCS', 'FURNITURE', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Desktop Computer', 'COMPUTER_DESKTOP', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Laptop', 'LAPTOP', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Projector', 'PROJECTOR', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Interactive Smart Board', 'SMART_BOARD', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Printer', 'PRINTER', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Photocopier', 'PHOTOCOPIER', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'CCTV Camera', 'CCTV_CAMERA', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Wi-Fi Router', 'WIFI_ROUTER', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Tablet', 'TABLET', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'PA System', 'PA_SYSTEM', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Ceiling Fan', 'CEILING_FAN', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Air Conditioner', 'AIR_CONDITIONER', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Water Purifier', 'WATER_PURIFIER', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Television', 'TELEVISION', 'PCS', 'ELECTRONICS', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Microscope', 'MICROSCOPE', 'PCS', 'LABORATORY', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Laboratory Balance', 'LAB_BALANCE', 'PCS', 'LABORATORY', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Bunsen Burner', 'BUNSEN_BURNER', 'PCS', 'LABORATORY', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Table Tennis Table', 'TABLE_TENNIS_TABLE', 'PCS', 'SPORTS', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Basketball Hoop', 'BASKETBALL_HOOP', 'PCS', 'SPORTS', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'Fire Extinguisher', 'FIRE_EXTINGUISHER', 'PCS', 'GENERAL', 'INVENTORY', NOW(6), NOW(6)),
('ASSET_TYPE', 'First Aid Box', 'FIRST_AID_BOX', 'PCS', 'GENERAL', 'INVENTORY', NOW(6), NOW(6));

-- An organisation whose template catalog was already copied from the master list will not be re-seeded,
-- so give it the new rows now. Organisations with no catalog yet get them on their first read. The
-- (organisation_id, category, name) unique key makes IGNORE skip anything an admin already added by hand.
INSERT IGNORE INTO school_template_items
  (organisation_id, category, name, code, unit, item_category_code, module_code, created_at, updated_at)
SELECT o.organisation_id, m.category, m.name, m.code, m.unit, m.item_category_code, m.module_code, NOW(6), NOW(6)
FROM (SELECT DISTINCT organisation_id FROM school_template_items) o
JOIN school_template_master_items m ON m.category IN ('ITEM', 'ASSET_TYPE');
