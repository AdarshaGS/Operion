-- Global, platform-wide reference catalog for the "School setup template" (see
-- com.operion.onboarding) - same convention as `permissions`: a shared table seeded here
-- by data, not by a Java class. Each organisation's own school_template_items (V99) is
-- bootstrapped once by copying these rows (SchoolTemplateItemService.ensureSeeded()), then
-- becomes independently editable - this table itself is never written to by the app.
CREATE TABLE school_template_master_items (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    category          VARCHAR(20) NOT NULL,
    name              VARCHAR(150) NOT NULL,
    code              VARCHAR(50),
    description       VARCHAR(500),
    sequence_order    INT,
    stage             VARCHAR(50),
    category_type     VARCHAR(20),
    min_percentage    DOUBLE,
    remark            VARCHAR(255),
    permission_codes  VARCHAR(2000),
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL
) ENGINE = InnoDB;

-- Grades (12)
INSERT INTO school_template_master_items (category, name, sequence_order, stage, created_at, updated_at) VALUES
    ('GRADE', 'LKG', 0, 'Pre-Primary', NOW(6), NOW(6)),
    ('GRADE', 'UKG', 1, 'Pre-Primary', NOW(6), NOW(6)),
    ('GRADE', 'Grade 1', 2, 'Primary', NOW(6), NOW(6)),
    ('GRADE', 'Grade 2', 3, 'Primary', NOW(6), NOW(6)),
    ('GRADE', 'Grade 3', 4, 'Primary', NOW(6), NOW(6)),
    ('GRADE', 'Grade 4', 5, 'Primary', NOW(6), NOW(6)),
    ('GRADE', 'Grade 5', 6, 'Primary', NOW(6), NOW(6)),
    ('GRADE', 'Grade 6', 7, 'Secondary', NOW(6), NOW(6)),
    ('GRADE', 'Grade 7', 8, 'Secondary', NOW(6), NOW(6)),
    ('GRADE', 'Grade 8', 9, 'Secondary', NOW(6), NOW(6)),
    ('GRADE', 'Grade 9', 10, 'Secondary', NOW(6), NOW(6)),
    ('GRADE', 'Grade 10', 11, 'Secondary', NOW(6), NOW(6));

-- Departments (10)
INSERT INTO school_template_master_items (category, name, created_at, updated_at) VALUES
    ('DEPARTMENT', 'Administration', NOW(6), NOW(6)),
    ('DEPARTMENT', 'Academics', NOW(6), NOW(6)),
    ('DEPARTMENT', 'Finance & Accounts', NOW(6), NOW(6)),
    ('DEPARTMENT', 'HR', NOW(6), NOW(6)),
    ('DEPARTMENT', 'Operations', NOW(6), NOW(6)),
    ('DEPARTMENT', 'Transport', NOW(6), NOW(6)),
    ('DEPARTMENT', 'Library', NOW(6), NOW(6)),
    ('DEPARTMENT', 'IT', NOW(6), NOW(6)),
    ('DEPARTMENT', 'Examination', NOW(6), NOW(6)),
    ('DEPARTMENT', 'Admissions', NOW(6), NOW(6));

-- Designations (17)
INSERT INTO school_template_master_items (category, name, created_at, updated_at) VALUES
    ('DESIGNATION', 'Principal', NOW(6), NOW(6)),
    ('DESIGNATION', 'Vice Principal', NOW(6), NOW(6)),
    ('DESIGNATION', 'HOD', NOW(6), NOW(6)),
    ('DESIGNATION', 'Academic Coordinator', NOW(6), NOW(6)),
    ('DESIGNATION', 'Teacher', NOW(6), NOW(6)),
    ('DESIGNATION', 'Assistant Teacher', NOW(6), NOW(6)),
    ('DESIGNATION', 'Accountant', NOW(6), NOW(6)),
    ('DESIGNATION', 'HR Manager', NOW(6), NOW(6)),
    ('DESIGNATION', 'Admin Officer', NOW(6), NOW(6)),
    ('DESIGNATION', 'Receptionist', NOW(6), NOW(6)),
    ('DESIGNATION', 'Librarian', NOW(6), NOW(6)),
    ('DESIGNATION', 'Transport Manager', NOW(6), NOW(6)),
    ('DESIGNATION', 'Driver', NOW(6), NOW(6)),
    ('DESIGNATION', 'Storekeeper', NOW(6), NOW(6)),
    ('DESIGNATION', 'Purchase Manager', NOW(6), NOW(6)),
    ('DESIGNATION', 'IT Admin', NOW(6), NOW(6)),
    ('DESIGNATION', 'Support Staff', NOW(6), NOW(6));

-- Roles (12) - "Organisation Owner" is deliberately not here; it aliases the existing
-- system "Owner" role every org already has from provisioning (see
-- SchoolTemplateService.applyRoles()).
INSERT INTO school_template_master_items (category, name, description, permission_codes, created_at, updated_at) VALUES
    ('ROLE', 'Administrator', 'Broad day-to-day operational access, short of financial approvals and payroll',
        'ORGANISATION_MANAGE,ROLE_MANAGE,MEMBERSHIP_MANAGE,MEMBERSHIP_VIEW,PROFILE_CHANGE_MANAGE,ACADEMIC_CONFIGURATION_MANAGE,ACADEMIC_CONFIGURATION_VIEW,CLASS_MANAGE,CLASS_VIEW,GRADE_LEVEL_MANAGE,GRADE_LEVEL_VIEW,SUBJECT_MANAGE,SUBJECT_VIEW,TEACHER_ASSIGNMENT_MANAGE,TEACHER_ASSIGNMENT_VIEW,WORKING_CALENDAR_MANAGE,WORKING_CALENDAR_VIEW,STUDENT_MANAGE,STUDENT_CREATE,STUDENT_UPDATE,STUDENT_VIEW,STUDENT_ENROLLMENT_MANAGE,STUDENT_TRANSFER_MANAGE,STUDENT_EXIT_MANAGE,STUDENT_DOCUMENT_MANAGE,STUDENT_DOCUMENT_VIEW,ATTENDANCE_VIEW,ATTENDANCE_CORRECT,ATTENDANCE_LOCK,ATTENDANCE_UNLOCK,STAFF_ATTENDANCE_VIEW,FEE_VIEW,FEE_STRUCTURE_MANAGE,FEE_CATEGORY_MANAGE,EXAM_VIEW,EXAM_MANAGE,GRADING_SCALE_MANAGE,HR_VIEW,HR_STAFF_MANAGE,LIBRARY_VIEW,TRANSPORT_VIEW,COMMUNICATION_VIEW,ANNOUNCEMENT_CREATE,ANNOUNCEMENT_PUBLISH,ANNOUNCEMENT_CANCEL,NOTIFICATION_TEMPLATE_MANAGE,REPORT_VIEW,REPORT_CREATE,REPORT_MANAGE,REPORT_EXPORT',
        NOW(6), NOW(6)),
    ('ROLE', 'Principal', 'Academic leadership, approvals, and staff oversight',
        'ACADEMIC_CONFIGURATION_VIEW,CLASS_VIEW,GRADE_LEVEL_VIEW,SUBJECT_VIEW,TEACHER_ASSIGNMENT_VIEW,TEACHER_ASSIGNMENT_MANAGE,WORKING_CALENDAR_VIEW,STUDENT_VIEW,STUDENT_ENROLLMENT_MANAGE,STUDENT_TRANSFER_MANAGE,STUDENT_EXIT_MANAGE,ATTENDANCE_VIEW,ATTENDANCE_CORRECT,ATTENDANCE_LOCK,ATTENDANCE_UNLOCK,STAFF_ATTENDANCE_VIEW,EXAM_VIEW,EXAM_MANAGE,MARKS_APPROVE,MARKS_CORRECT_AFTER_PUBLISH,GRADING_SCALE_MANAGE,REPORT_CARD_PUBLISH,HR_VIEW,HR_LEAVE_MANAGE,GUARDIAN_VIEW,COMMUNICATION_VIEW,ANNOUNCEMENT_CREATE,ANNOUNCEMENT_PUBLISH,REPORT_VIEW,REPORT_CREATE,MEMBERSHIP_VIEW',
        NOW(6), NOW(6)),
    ('ROLE', 'Academic Admin', 'Day-to-day academic structure and exam configuration',
        'ACADEMIC_CONFIGURATION_MANAGE,ACADEMIC_CONFIGURATION_VIEW,CLASS_MANAGE,CLASS_VIEW,GRADE_LEVEL_MANAGE,GRADE_LEVEL_VIEW,SUBJECT_MANAGE,SUBJECT_VIEW,TEACHER_ASSIGNMENT_MANAGE,TEACHER_ASSIGNMENT_VIEW,WORKING_CALENDAR_MANAGE,WORKING_CALENDAR_VIEW,EXAM_VIEW,EXAM_MANAGE,MARKS_CORRECT,GRADING_SCALE_MANAGE,REPORT_CARD_PUBLISH,STUDENT_VIEW,ATTENDANCE_VIEW,COMMUNICATION_VIEW,ANNOUNCEMENT_CREATE',
        NOW(6), NOW(6)),
    ('ROLE', 'Teacher', 'Classroom attendance, marks entry, and guardian communication',
        'CLASS_VIEW,SUBJECT_VIEW,STUDENT_VIEW,STUDENT_DOCUMENT_VIEW,ATTENDANCE_MARK,ATTENDANCE_VIEW,EXAM_VIEW,MARKS_ENTER,MARKS_SUBMIT,GUARDIAN_VIEW,COMMUNICATION_VIEW,ANNOUNCEMENT_CREATE',
        NOW(6), NOW(6)),
    ('ROLE', 'Accountant', 'Fee collection, invoicing, and financial approvals',
        'FEE_VIEW,FEE_CATEGORY_MANAGE,FEE_STRUCTURE_MANAGE,FEE_INVOICE_MANAGE,FEE_COLLECT,FEE_REMINDER_SEND,FEE_ADJUSTMENT_MANAGE,FEE_REFUND_APPROVE,FEE_WAIVER_APPROVE,FEE_DISCOUNT_APPROVE,FEE_ASSIGNMENT_MANAGE,STUDENT_VIEW,REPORT_VIEW,REPORT_CREATE',
        NOW(6), NOW(6)),
    ('ROLE', 'HR Manager', 'Staff records, leave, and recruitment',
        'HR_VIEW,HR_STAFF_MANAGE,HR_LEAVE_MANAGE,HR_LEAVE_TYPE_MANAGE,HR_RECRUITMENT_MANAGE,HR_PAYROLL_VIEW,MEMBERSHIP_VIEW,PROFILE_CHANGE_MANAGE,REPORT_VIEW',
        NOW(6), NOW(6)),
    ('ROLE', 'Librarian', 'Library catalog, borrowing, and fines',
        'LIBRARY_VIEW,LIBRARY_CATALOG_MANAGE,LIBRARY_BORROW_MANAGE,LIBRARY_FINE_MANAGE,STUDENT_VIEW',
        NOW(6), NOW(6)),
    ('ROLE', 'Transport Manager', 'Vehicles, routes, and student transport assignments',
        'TRANSPORT_VIEW,TRANSPORT_ROUTE_MANAGE,TRANSPORT_VEHICLE_MANAGE,TRANSPORT_ASSIGNMENT_MANAGE,TRANSPORT_TRIP_LOG,STUDENT_VIEW',
        NOW(6), NOW(6)),
    ('ROLE', 'Inventory Manager', 'Item catalog, stock entries, and adjustments',
        'INVENTORY_VIEW,INVENTORY_CATALOG_MANAGE,INVENTORY_STOCK_MANAGE,INVENTORY_ADJUSTMENT_MANAGE,INVENTORY_SUPPLIER_MANAGE,INVENTORY_CUSTOMER_MANAGE',
        NOW(6), NOW(6)),
    ('ROLE', 'Purchase Manager', 'Purchase orders and supplier returns',
        'PURCHASE_VIEW,PURCHASE_MANAGE,INVENTORY_VIEW',
        NOW(6), NOW(6)),
    ('ROLE', 'Receptionist', 'Front-desk enquiries and visitor communication',
        'STUDENT_VIEW,GUARDIAN_VIEW,MEMBERSHIP_VIEW,ATTENDANCE_VIEW,FEE_VIEW,COMMUNICATION_VIEW,ANNOUNCEMENT_CREATE',
        NOW(6), NOW(6)),
    ('ROLE', 'Staff', 'Baseline read-only access for general staff',
        'STUDENT_VIEW,COMMUNICATION_VIEW,ATTENDANCE_VIEW',
        NOW(6), NOW(6));

-- Fee categories (6)
INSERT INTO school_template_master_items (category, name, code, description, category_type, created_at, updated_at) VALUES
    ('FEE_CATEGORY', 'Tuition Fee', 'TUITION', 'Recurring academic tuition charge', 'GENERAL', NOW(6), NOW(6)),
    ('FEE_CATEGORY', 'Admission Fee', 'ADMISSION', 'One-time fee charged at admission', 'GENERAL', NOW(6), NOW(6)),
    ('FEE_CATEGORY', 'Examination Fee', 'EXAMINATION', 'Per-exam or per-term examination charge', 'GENERAL', NOW(6), NOW(6)),
    ('FEE_CATEGORY', 'Transport Fee', 'TRANSPORT', 'School transport charge', 'TRANSPORT', NOW(6), NOW(6)),
    ('FEE_CATEGORY', 'Library Fee', 'LIBRARY', 'Library membership/usage charge', 'GENERAL', NOW(6), NOW(6)),
    ('FEE_CATEGORY', 'Other Fee', 'OTHER', 'Miscellaneous charges not covered above', 'GENERAL', NOW(6), NOW(6));

-- Inventory item categories (7)
INSERT INTO school_template_master_items (category, name, code, created_at, updated_at) VALUES
    ('ITEM_CATEGORY', 'Stationery', 'STATIONERY', NOW(6), NOW(6)),
    ('ITEM_CATEGORY', 'Furniture', 'FURNITURE', NOW(6), NOW(6)),
    ('ITEM_CATEGORY', 'Electronics', 'ELECTRONICS', NOW(6), NOW(6)),
    ('ITEM_CATEGORY', 'Cleaning Supplies', 'CLEANING_SUPPLIES', NOW(6), NOW(6)),
    ('ITEM_CATEGORY', 'Sports', 'SPORTS', NOW(6), NOW(6)),
    ('ITEM_CATEGORY', 'Laboratory', 'LABORATORY', NOW(6), NOW(6)),
    ('ITEM_CATEGORY', 'General', 'GENERAL', NOW(6), NOW(6));

-- Default grading scale bands (8)
INSERT INTO school_template_master_items (category, name, sequence_order, min_percentage, remark, created_at, updated_at) VALUES
    ('GRADING_BAND', 'A+', 0, 90, 'Outstanding', NOW(6), NOW(6)),
    ('GRADING_BAND', 'A', 1, 80, 'Excellent', NOW(6), NOW(6)),
    ('GRADING_BAND', 'B+', 2, 70, 'Very good', NOW(6), NOW(6)),
    ('GRADING_BAND', 'B', 3, 60, 'Good', NOW(6), NOW(6)),
    ('GRADING_BAND', 'C+', 4, 50, 'Above average', NOW(6), NOW(6)),
    ('GRADING_BAND', 'C', 5, 40, 'Average', NOW(6), NOW(6)),
    ('GRADING_BAND', 'D', 6, 33, 'Pass', NOW(6), NOW(6)),
    ('GRADING_BAND', 'F', 7, 0, 'Fail', NOW(6), NOW(6));
