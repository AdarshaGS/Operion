-- School Starter Pack: module-aware setup (GitHub #198 follow-up).
--
-- 1. Template items can be tagged with the module they belong to (AppModule code). Applying
--    the pack skips any item whose module is switched off for the organisation, so a school
--    without Transport never gets a Driver designation or a Transport fee category.
--    NULL = not tied to a module, always applied.
-- 2. The "needs" questions the onboarding step asks, and which modules each answer turns on,
--    live here as data (same convention as school_template_master_items) rather than in code.

ALTER TABLE school_template_master_items ADD COLUMN module_code VARCHAR(40);
ALTER TABLE school_template_items ADD COLUMN module_code VARCHAR(40);

-- Same tagging for the global catalog and for every organisation's already-copied rows.
UPDATE school_template_master_items SET module_code = 'TRANSPORT' WHERE (category = 'DEPARTMENT' AND name = 'Transport')
    OR (category = 'DESIGNATION' AND name IN ('Transport Manager', 'Driver'))
    OR (category = 'ROLE' AND name = 'Transport Manager') OR (category = 'FEE_CATEGORY' AND code = 'TRANSPORT');
UPDATE school_template_master_items SET module_code = 'LIBRARY' WHERE (category = 'DEPARTMENT' AND name = 'Library')
    OR (category = 'DESIGNATION' AND name = 'Librarian') OR (category = 'ROLE' AND name = 'Librarian')
    OR (category = 'FEE_CATEGORY' AND code = 'LIBRARY');
UPDATE school_template_master_items SET module_code = 'HR' WHERE (category = 'DEPARTMENT' AND name = 'HR')
    OR (category = 'DESIGNATION' AND name = 'HR Manager') OR (category = 'ROLE' AND name = 'HR Manager');
UPDATE school_template_master_items SET module_code = 'EXAMINATIONS' WHERE (category = 'DEPARTMENT' AND name = 'Examination')
    OR (category = 'FEE_CATEGORY' AND code = 'EXAMINATION');
UPDATE school_template_master_items SET module_code = 'FEES' WHERE (category = 'DESIGNATION' AND name = 'Accountant')
    OR (category = 'ROLE' AND name = 'Accountant');
UPDATE school_template_master_items SET module_code = 'INVENTORY' WHERE (category = 'DESIGNATION' AND name = 'Storekeeper')
    OR (category = 'ROLE' AND name = 'Inventory Manager');
UPDATE school_template_master_items SET module_code = 'PURCHASE' WHERE (category = 'DESIGNATION' AND name = 'Purchase Manager')
    OR (category = 'ROLE' AND name = 'Purchase Manager');

UPDATE school_template_items SET module_code = 'TRANSPORT' WHERE (category = 'DEPARTMENT' AND name = 'Transport')
    OR (category = 'DESIGNATION' AND name IN ('Transport Manager', 'Driver'))
    OR (category = 'ROLE' AND name = 'Transport Manager') OR (category = 'FEE_CATEGORY' AND code = 'TRANSPORT');
UPDATE school_template_items SET module_code = 'LIBRARY' WHERE (category = 'DEPARTMENT' AND name = 'Library')
    OR (category = 'DESIGNATION' AND name = 'Librarian') OR (category = 'ROLE' AND name = 'Librarian')
    OR (category = 'FEE_CATEGORY' AND code = 'LIBRARY');
UPDATE school_template_items SET module_code = 'HR' WHERE (category = 'DEPARTMENT' AND name = 'HR')
    OR (category = 'DESIGNATION' AND name = 'HR Manager') OR (category = 'ROLE' AND name = 'HR Manager');
UPDATE school_template_items SET module_code = 'EXAMINATIONS' WHERE (category = 'DEPARTMENT' AND name = 'Examination')
    OR (category = 'FEE_CATEGORY' AND code = 'EXAMINATION');
UPDATE school_template_items SET module_code = 'FEES' WHERE (category = 'DESIGNATION' AND name = 'Accountant')
    OR (category = 'ROLE' AND name = 'Accountant');
UPDATE school_template_items SET module_code = 'INVENTORY' WHERE (category = 'DESIGNATION' AND name = 'Storekeeper')
    OR (category = 'ROLE' AND name = 'Inventory Manager');
UPDATE school_template_items SET module_code = 'PURCHASE' WHERE (category = 'DESIGNATION' AND name = 'Purchase Manager')
    OR (category = 'ROLE' AND name = 'Purchase Manager');

-- One row per question the onboarding step asks. baseline rows are never asked: their modules
-- are always included. module_codes is a comma-separated AppModule list (same shape as
-- permission_codes above - a read-only catalog row has no FK integrity to protect).
CREATE TABLE school_starter_needs (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    need_key       VARCHAR(40)  NOT NULL UNIQUE,
    label          VARCHAR(200) NOT NULL,
    help_text      VARCHAR(500),
    default_yes    BOOLEAN      NOT NULL,
    baseline       BOOLEAN      NOT NULL,
    display_order  INT          NOT NULL,
    module_codes   VARCHAR(200) NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL
) ENGINE = InnoDB;

INSERT INTO school_starter_needs (need_key, label, help_text, default_yes, baseline, display_order, module_codes, created_at, updated_at) VALUES
    ('BASELINE', 'Core school operations', 'Included for every school.', TRUE, TRUE, 0, 'DASHBOARD,STUDENTS,ACADEMICS,ATTENDANCE,REPORTS', NOW(6), NOW(6)),
    ('FEES', 'Do you collect fees through Operion?', 'Fee structures, invoices, receipts and dues.', TRUE, FALSE, 10, 'FEES', NOW(6), NOW(6)),
    ('EXAMS', 'Do you run exams and issue report cards?', 'Exam schedules, marks entry, grading and report cards.', TRUE, FALSE, 20, 'EXAMINATIONS', NOW(6), NOW(6)),
    ('LEARNING', 'Do teachers share homework, assignments or study material?', NULL, TRUE, FALSE, 30, 'LEARNING', NOW(6), NOW(6)),
    ('COMMUNICATION', 'Do you send announcements and messages to parents and staff?', NULL, TRUE, FALSE, 40, 'COMMUNICATION,MESSAGING', NOW(6), NOW(6)),
    ('TRANSPORT', 'Do you run school buses or arrange student transport?', 'Vehicles, routes and student transport assignments. Needs Fees.', FALSE, FALSE, 50, 'TRANSPORT', NOW(6), NOW(6)),
    ('LIBRARY', 'Do you have a library with book borrowing?', NULL, FALSE, FALSE, 60, 'LIBRARY', NOW(6), NOW(6)),
    ('HR', 'Do you manage staff records and leave here?', NULL, TRUE, FALSE, 70, 'HR', NOW(6), NOW(6)),
    ('PAYROLL', 'Do you run staff payroll here?', 'Needs HR.', FALSE, FALSE, 80, 'PAYROLL', NOW(6), NOW(6)),
    ('INVENTORY', 'Do you track stores and stock?', NULL, FALSE, FALSE, 90, 'INVENTORY', NOW(6), NOW(6)),
    ('PROCUREMENT', 'Do you buy from suppliers or sell items such as uniforms and books?', 'Purchase orders and sales. Needs Inventory.', FALSE, FALSE, 100, 'PURCHASE,SALES', NOW(6), NOW(6)),
    ('COMPLIANCE', 'Do you need regulatory exports, consent records or data-subject requests?', NULL, FALSE, FALSE, 110, 'COMPLIANCE', NOW(6), NOW(6));
