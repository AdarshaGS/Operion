-- School database baseline (two-database split). Generated from the single-database schema as of V131,
-- minus every foreign key that crosses the platform/school boundary (integrity across the two
-- databases is enforced in the application, not by the database).

CREATE TABLE `academic_configurations` (
  `organisation_id` bigint NOT NULL,
  `school_start_time` time DEFAULT NULL,
  `school_end_time` time DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `academic_years` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `name` varchar(50) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `is_current` tinyint(1) NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_academic_years_org_name` (`organisation_id`,`name`),
  KEY `idx_academic_years_org_current` (`organisation_id`,`is_current`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `adjustments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `invoice_id` bigint NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `approved_by` bigint NOT NULL,
  `adjustment_date` date NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `proof_file_reference` varchar(255) DEFAULT NULL,
  `proof_file_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_adjustments_organisation` (`organisation_id`),
  KEY `idx_adjustments_invoice` (`invoice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `announcement_audience_members` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `announcement_id` bigint NOT NULL,
  `person_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_announcement_audience_members_announcement_person` (`announcement_id`,`person_id`),
  KEY `fk_announcement_audience_members_person` (`person_id`),
  KEY `idx_announcement_audience_members_announcement` (`organisation_id`,`announcement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `announcements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `campus_id` bigint DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `body` text NOT NULL,
  `audience_type` varchar(20) NOT NULL,
  `audience_ref_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `published_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `scheduled_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_announcements_campus` (`campus_id`),
  KEY `idx_announcements_campus_status` (`organisation_id`,`campus_id`,`status`),
  KEY `idx_announcements_status_scheduled_at` (`organisation_id`,`status`,`scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `answer_sheet_annotations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `page_id` bigint NOT NULL,
  `kind` varchar(20) NOT NULL,
  `pos_x` double NOT NULL,
  `pos_y` double NOT NULL,
  `label` varchar(500) DEFAULT NULL,
  `marks` double DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_answer_sheet_annotations_organisation` (`organisation_id`),
  KEY `idx_answer_sheet_annotations_page` (`page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `answer_sheet_pages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `sheet_id` bigint NOT NULL,
  `page_number` int NOT NULL,
  `asset_ref` varchar(255) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_answer_sheet_pages_organisation` (`organisation_id`),
  KEY `idx_answer_sheet_pages_sheet` (`sheet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `answer_sheets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `exam_schedule_id` bigint NOT NULL,
  `student_enrollment_id` bigint NOT NULL,
  `status` varchar(20) NOT NULL,
  `total_marks` double DEFAULT NULL,
  `overall_remarks` varchar(2000) DEFAULT NULL,
  `uploaded_by_person_id` bigint NOT NULL,
  `marked_by_person_id` bigint DEFAULT NULL,
  `marked_at` datetime(6) DEFAULT NULL,
  `released_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_answer_sheets_schedule_enrollment` (`exam_schedule_id`,`student_enrollment_id`),
  KEY `fk_answer_sheets_enrollment` (`student_enrollment_id`),
  KEY `fk_answer_sheets_uploader` (`uploaded_by_person_id`),
  KEY `fk_answer_sheets_marker` (`marked_by_person_id`),
  KEY `idx_answer_sheets_enrollment` (`organisation_id`,`student_enrollment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `applicant_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `applicant_id` bigint NOT NULL,
  `doc_type` varchar(50) NOT NULL,
  `received` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_applicant_documents_applicant_type` (`applicant_id`,`doc_type`),
  KEY `fk_applicant_documents_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `applicants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `person_id` bigint NOT NULL,
  `inquiry_date` date NOT NULL,
  `source` varchar(100) DEFAULT NULL,
  `notes` text,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `desired_grade_id` bigint DEFAULT NULL,
  `guardian_name` varchar(200) DEFAULT NULL,
  `guardian_phone` varchar(30) DEFAULT NULL,
  `guardian_email` varchar(200) DEFAULT NULL,
  `rejection_reason` varchar(1000) DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_applicants_organisation` (`organisation_id`),
  KEY `fk_applicants_person` (`person_id`),
  KEY `fk_applicants_desired_grade` (`desired_grade_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `campus_id` bigint NOT NULL,
  `asset_tag` varchar(100) NOT NULL,
  `assigned_person_id` bigint DEFAULT NULL,
  `assigned_location` varchar(255) DEFAULT NULL,
  `purchase_date` date DEFAULT NULL,
  `warranty_expiry_date` date DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_assets_org_tag` (`organisation_id`,`asset_tag`),
  KEY `fk_assets_item` (`item_id`),
  KEY `fk_assets_campus` (`campus_id`),
  KEY `fk_assets_assigned_person` (`assigned_person_id`),
  KEY `idx_assets_campus` (`organisation_id`,`campus_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `attendance_corrections` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `student_attendance_id` bigint NOT NULL,
  `previous_status` varchar(20) NOT NULL,
  `new_status` varchar(20) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_attendance_corrections_organisation` (`organisation_id`),
  KEY `idx_attendance_corrections_student_attendance` (`student_attendance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint DEFAULT NULL,
  `actor_user_id` bigint DEFAULT NULL,
  `entity_type` varchar(100) NOT NULL,
  `entity_id` bigint NOT NULL,
  `action` varchar(50) NOT NULL,
  `before_value` json DEFAULT NULL,
  `after_value` json DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `ip_address` varchar(64) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_logs_org_entity` (`organisation_id`,`entity_type`,`entity_id`),
  KEY `idx_audit_logs_org_occurred` (`organisation_id`,`occurred_at`),
  KEY `idx_audit_logs_actor` (`actor_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `book_copies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `book_id` bigint NOT NULL,
  `campus_id` bigint NOT NULL,
  `accession_number` varchar(30) NOT NULL,
  `status` varchar(20) NOT NULL,
  `acquired_date` date DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `shelf_location` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_book_copies_org_accession` (`organisation_id`,`accession_number`),
  KEY `fk_book_copies_book` (`book_id`),
  KEY `fk_book_copies_campus` (`campus_id`),
  KEY `idx_book_copies_book_status` (`organisation_id`,`book_id`,`status`),
  KEY `idx_book_copies_campus_status` (`organisation_id`,`campus_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `books` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `isbn` varchar(20) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `author` varchar(200) DEFAULT NULL,
  `publisher` varchar(200) DEFAULT NULL,
  `category` varchar(100) DEFAULT NULL,
  `edition` varchar(50) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_books_isbn` (`organisation_id`,`isbn`),
  KEY `idx_books_title` (`organisation_id`,`title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `borrow_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `book_copy_id` bigint NOT NULL,
  `borrower_person_id` bigint NOT NULL,
  `borrowed_date` date NOT NULL,
  `due_date` date NOT NULL,
  `returned_date` date DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_borrow_records_book_copy` (`book_copy_id`),
  KEY `fk_borrow_records_borrower` (`borrower_person_id`),
  KEY `idx_borrow_records_copy_status` (`organisation_id`,`book_copy_id`,`status`),
  KEY `idx_borrow_records_borrower_status` (`organisation_id`,`borrower_person_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `campuses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `name` varchar(255) NOT NULL,
  `code` varchar(50) NOT NULL,
  `address_line1` varchar(255) DEFAULT NULL,
  `address_line2` varchar(255) DEFAULT NULL,
  `city` varchar(100) DEFAULT NULL,
  `state` varchar(100) DEFAULT NULL,
  `pincode` varchar(20) DEFAULT NULL,
  `timezone` varchar(64) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_campuses_org_code` (`organisation_id`,`code`),
  KEY `idx_campuses_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `class_attendance_registers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `academic_year_id` bigint NOT NULL,
  `section_id` bigint NOT NULL,
  `attendance_date` date NOT NULL,
  `register_status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_class_attendance_registers_section_date` (`section_id`,`attendance_date`),
  KEY `fk_class_attendance_registers_organisation` (`organisation_id`),
  KEY `fk_class_attendance_registers_academic_year` (`academic_year_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `class_subjects` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `school_class_id` bigint NOT NULL,
  `subject_id` bigint NOT NULL,
  `is_mandatory` tinyint(1) NOT NULL DEFAULT '1',
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_class_subjects_class_subject` (`school_class_id`,`subject_id`),
  KEY `fk_class_subjects_organisation` (`organisation_id`),
  KEY `fk_class_subjects_subject` (`subject_id`),
  KEY `idx_class_subjects_school_class` (`school_class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `consent_purposes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `code` varchar(60) NOT NULL,
  `name` varchar(200) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_consent_purposes_code` (`organisation_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `consent_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `person_id` bigint NOT NULL,
  `purpose_id` bigint NOT NULL,
  `granted` tinyint(1) NOT NULL,
  `source` varchar(30) NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `recorded_by` bigint DEFAULT NULL,
  `recorded_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_consent_records_person` (`person_id`),
  KEY `fk_consent_records_purpose` (`purpose_id`),
  KEY `idx_consent_records_person` (`organisation_id`,`person_id`,`purpose_id`,`recorded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `custom_field_definitions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `entity_type` varchar(50) NOT NULL,
  `field_key` varchar(100) NOT NULL,
  `label` varchar(150) NOT NULL,
  `data_type` varchar(20) NOT NULL,
  `options` varchar(500) DEFAULT NULL,
  `required` tinyint(1) NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_custom_field_definitions_entity_key` (`organisation_id`,`entity_type`,`field_key`),
  KEY `idx_custom_field_definitions_organisation` (`organisation_id`),
  KEY `idx_custom_field_definitions_entity_type` (`entity_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `customers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `student_id` bigint DEFAULT NULL,
  `guardian_id` bigint DEFAULT NULL,
  `name` varchar(200) NOT NULL,
  `phone` varchar(30) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_customers_student` (`student_id`),
  KEY `fk_customers_guardian` (`guardian_id`),
  KEY `idx_customers_org_status` (`organisation_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `dashboard_preferences` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `setup_progress_dismissed` tinyint(1) NOT NULL DEFAULT '0',
  `quick_actions_dismissed` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_dashboard_preferences_org_user` (`organisation_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `data_subject_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `person_id` bigint NOT NULL,
  `request_type` varchar(20) NOT NULL,
  `status` varchar(30) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `received_date` date NOT NULL,
  `due_date` date NOT NULL,
  `identity_verified_by` bigint DEFAULT NULL,
  `identity_verified_at` datetime(6) DEFAULT NULL,
  `identity_verification_method` varchar(200) DEFAULT NULL,
  `resolved_by` bigint DEFAULT NULL,
  `resolved_at` datetime(6) DEFAULT NULL,
  `resolution_note` varchar(1000) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_dsr_person` (`person_id`),
  KEY `idx_dsr_status` (`organisation_id`,`status`,`due_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `departments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `name` varchar(255) NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_departments_org_status` (`organisation_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `designations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `name` varchar(255) NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_designations_org_status` (`organisation_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `document_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `document_type` varchar(30) NOT NULL,
  `template_style` varchar(20) NOT NULL,
  `page_size` varchar(20) NOT NULL,
  `font_style` varchar(50) NOT NULL,
  `font_size` int NOT NULL,
  `header_subtext` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_document_templates_org_type` (`organisation_id`,`document_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `driver_profiles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `person_id` bigint NOT NULL,
  `licence_number` varchar(50) NOT NULL,
  `licence_expiry_date` date NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_driver_profiles_person` (`person_id`),
  KEY `fk_driver_profiles_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `email_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `recipient` varchar(255) NOT NULL,
  `subject` varchar(255) NOT NULL,
  `status` varchar(20) NOT NULL,
  `provider` varchar(20) DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `failure_reason` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_email_outbox_org_status` (`organisation_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `email_verification_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `token_hash` varchar(255) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `consumed` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_email_verification_tokens_user` (`user_id`),
  KEY `idx_email_verification_tokens_org_consumed` (`organisation_id`,`consumed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `exam_schedules` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `exam_id` bigint NOT NULL,
  `school_class_id` bigint NOT NULL,
  `section_id` bigint DEFAULT NULL,
  `subject_id` bigint NOT NULL,
  `exam_date` date NOT NULL,
  `max_marks` double NOT NULL,
  `pass_marks` double NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_exam_schedules_exam_class_subject_section` (`exam_id`,`school_class_id`,`subject_id`,`section_id`),
  KEY `fk_exam_schedules_organisation` (`organisation_id`),
  KEY `fk_exam_schedules_school_class` (`school_class_id`),
  KEY `fk_exam_schedules_subject` (`subject_id`),
  KEY `fk_exam_schedules_section` (`section_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `examination_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `ranking_enabled` tinyint(1) NOT NULL DEFAULT '0',
  `pass_fail_strategy` varchar(30) NOT NULL DEFAULT 'PASS_EVERY_SUBJECT',
  `minimum_aggregate_percentage` double NOT NULL DEFAULT '33',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_examination_settings_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `exams` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `academic_year_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `exam_type` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_exams_organisation` (`organisation_id`),
  KEY `idx_exams_academic_year` (`academic_year_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `fee_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `code` varchar(50) NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `category_type` varchar(20) NOT NULL DEFAULT 'GENERAL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_fee_categories_org_code` (`organisation_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `fee_document_counters` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `academic_year_id` bigint NOT NULL,
  `document_type` varchar(20) NOT NULL,
  `next_number` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_fee_document_counters_year_type` (`organisation_id`,`academic_year_id`,`document_type`),
  KEY `fk_fee_document_counters_academic_year` (`academic_year_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `fee_structure_groups` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `academic_year_id` bigint NOT NULL,
  `school_class_id` bigint NOT NULL,
  `name` varchar(150) NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_fee_structure_groups_year_class` (`organisation_id`,`academic_year_id`,`school_class_id`),
  KEY `fk_fee_structure_groups_academic_year` (`academic_year_id`),
  KEY `fk_fee_structure_groups_school_class` (`school_class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `fee_structure_installments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `fee_structure_id` bigint NOT NULL,
  `installment_number` int NOT NULL,
  `due_date` date NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_fee_structure_installments_structure_number` (`fee_structure_id`,`installment_number`),
  KEY `fk_fee_structure_installments_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `fee_structures` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `fee_category_id` bigint NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `fee_structure_group_id` bigint NOT NULL,
  `payment_frequency` varchar(20) NOT NULL DEFAULT 'MONTHLY',
  `one_shot_discount_amount` decimal(10,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_fee_structures_group_category` (`organisation_id`,`fee_structure_group_id`,`fee_category_id`),
  KEY `fk_fee_structures_fee_category` (`fee_category_id`),
  KEY `fk_fee_structures_group` (`fee_structure_group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `fines` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `borrow_record_id` bigint NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `reason` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL,
  `paid_date` date DEFAULT NULL,
  `waived_by` bigint DEFAULT NULL,
  `waived_reason` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_fines_borrow_record` (`borrow_record_id`),
  KEY `idx_fines_borrow_record` (`organisation_id`,`borrow_record_id`),
  KEY `idx_fines_status` (`organisation_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `grade_levels` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `sequence_order` int NOT NULL,
  `stage` varchar(50) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_grade_levels_org_sequence` (`organisation_id`,`sequence_order`),
  UNIQUE KEY `uq_grade_levels_org_name` (`organisation_id`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `grading_scale_bands` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `grading_scale_id` bigint NOT NULL,
  `grade` varchar(10) NOT NULL,
  `min_percentage` double NOT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_grading_scale_bands_scale_grade` (`grading_scale_id`,`grade`),
  UNIQUE KEY `uq_grading_scale_bands_scale_min` (`grading_scale_id`,`min_percentage`),
  KEY `fk_grading_scale_bands_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `grading_scales` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_grading_scales_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `guardians` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `person_id` bigint NOT NULL,
  `occupation` varchar(100) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_guardians_person` (`person_id`),
  KEY `fk_guardians_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `homework` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `section_id` bigint NOT NULL,
  `subject_id` bigint NOT NULL,
  `title` varchar(250) NOT NULL,
  `description` varchar(4000) DEFAULT NULL,
  `due_date` date NOT NULL,
  `attachment_ref` varchar(255) DEFAULT NULL,
  `assigned_by_person_id` bigint NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_homework_section` (`section_id`),
  KEY `fk_homework_subject` (`subject_id`),
  KEY `fk_homework_assigned_by` (`assigned_by_person_id`),
  KEY `idx_homework_section` (`organisation_id`,`section_id`,`due_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `homework_submissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `homework_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `submitted_by_person_id` bigint NOT NULL,
  `submission_text` varchar(4000) DEFAULT NULL,
  `attachment_ref` varchar(255) DEFAULT NULL,
  `submitted_at` datetime(6) NOT NULL,
  `late` tinyint(1) NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL,
  `feedback` varchar(2000) DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_homework_submissions_student` (`homework_id`,`student_id`),
  KEY `fk_homework_submissions_organisation` (`organisation_id`),
  KEY `fk_homework_submissions_student` (`student_id`),
  KEY `fk_homework_submissions_by` (`submitted_by_person_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `id_card_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `name` varchar(255) NOT NULL,
  `width_mm` double NOT NULL,
  `height_mm` double NOT NULL,
  `layout_json` text NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_id_card_templates_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `import_runs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `entity_type` varchar(100) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `total_rows` int NOT NULL,
  `imported_count` int NOT NULL,
  `error_count` int NOT NULL,
  `duplicate_count` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_import_runs_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `invoices` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `academic_year_id` bigint NOT NULL,
  `student_fee_assignment_id` bigint NOT NULL,
  `fee_structure_installment_id` bigint NOT NULL,
  `invoice_number` varchar(50) NOT NULL,
  `total_amount` decimal(10,2) NOT NULL,
  `amount_paid` decimal(10,2) NOT NULL DEFAULT '0.00',
  `due_date` date NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `overdue_reminder_sent_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_invoices_assignment_installment` (`student_fee_assignment_id`,`fee_structure_installment_id`),
  UNIQUE KEY `uq_invoices_org_number` (`organisation_id`,`invoice_number`),
  KEY `fk_invoices_academic_year` (`academic_year_id`),
  KEY `fk_invoices_installment` (`fee_structure_installment_id`),
  KEY `idx_invoices_org_year_due_date` (`organisation_id`,`academic_year_id`,`due_date`),
  KEY `idx_invoices_org_status` (`organisation_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `item_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `code` varchar(30) NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_item_categories_org_code` (`organisation_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `item_category_id` bigint NOT NULL,
  `code` varchar(30) NOT NULL,
  `name` varchar(150) NOT NULL,
  `unit` varchar(20) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `reorder_level` int DEFAULT NULL,
  `storage_location` varchar(255) DEFAULT NULL,
  `default_supplier_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_items_org_code` (`organisation_id`,`code`),
  KEY `fk_items_category` (`item_category_id`),
  KEY `idx_items_category` (`organisation_id`,`item_category_id`),
  KEY `fk_items_default_supplier` (`default_supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `job_applications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `applicant_name` varchar(255) NOT NULL,
  `email` varchar(255) NOT NULL,
  `specialization` varchar(255) DEFAULT NULL,
  `years_experience` int DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `applied_at` datetime(6) NOT NULL,
  `decided_by` bigint DEFAULT NULL,
  `decided_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_job_applications_status` (`organisation_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `learning_materials` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `school_class_id` bigint NOT NULL,
  `subject_id` bigint DEFAULT NULL,
  `title` varchar(250) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `kind` varchar(20) NOT NULL,
  `attachment_ref` varchar(255) DEFAULT NULL,
  `url` varchar(1000) DEFAULT NULL,
  `uploaded_by_person_id` bigint NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_learning_materials_class` (`school_class_id`),
  KEY `fk_learning_materials_subject` (`subject_id`),
  KEY `fk_learning_materials_uploaded_by` (`uploaded_by_person_id`),
  KEY `idx_learning_materials_class` (`organisation_id`,`school_class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `leave_balances` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `staff_profile_id` bigint NOT NULL,
  `leave_type_id` bigint NOT NULL,
  `academic_year_id` bigint NOT NULL,
  `allocated_days` double NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_leave_balances_staff_type_year` (`staff_profile_id`,`leave_type_id`,`academic_year_id`),
  KEY `fk_leave_balances_organisation` (`organisation_id`),
  KEY `fk_leave_balances_leave_type` (`leave_type_id`),
  KEY `fk_leave_balances_academic_year` (`academic_year_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `leave_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `staff_profile_id` bigint NOT NULL,
  `leave_type_id` bigint NOT NULL,
  `academic_year_id` bigint NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `number_of_days` double NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `approved_by` bigint DEFAULT NULL,
  `decided_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_leave_requests_staff_profile` (`staff_profile_id`),
  KEY `fk_leave_requests_leave_type` (`leave_type_id`),
  KEY `fk_leave_requests_academic_year` (`academic_year_id`),
  KEY `idx_leave_requests_staff_type_year_status` (`organisation_id`,`staff_profile_id`,`leave_type_id`,`academic_year_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `leave_types` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `code` varchar(30) NOT NULL,
  `name` varchar(100) NOT NULL,
  `default_annual_days` double DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_leave_types_org_code` (`organisation_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `lesson_plans` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `section_id` bigint NOT NULL,
  `subject_id` bigint NOT NULL,
  `plan_date` date NOT NULL,
  `title` varchar(250) NOT NULL,
  `objectives` varchar(2000) DEFAULT NULL,
  `activities` varchar(4000) DEFAULT NULL,
  `resources` varchar(2000) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `submitted_by_person_id` bigint NOT NULL,
  `submitted_at` datetime(6) DEFAULT NULL,
  `review_note` varchar(2000) DEFAULT NULL,
  `reviewed_by_person_id` bigint DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_lesson_plans_section` (`section_id`),
  KEY `fk_lesson_plans_subject` (`subject_id`),
  KEY `fk_lesson_plans_author` (`submitted_by_person_id`),
  KEY `fk_lesson_plans_reviewer` (`reviewed_by_person_id`),
  KEY `idx_lesson_plans_section_date` (`organisation_id`,`section_id`,`plan_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `library_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `max_loans_student` int NOT NULL DEFAULT '3',
  `max_loans_staff` int NOT NULL DEFAULT '5',
  `borrowing_period_days_student` int NOT NULL DEFAULT '14',
  `borrowing_period_days_staff` int NOT NULL DEFAULT '30',
  `block_issue_on_overdue` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_library_settings_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `login_attempts` (
  `attempt_key` varchar(300) NOT NULL,
  `organisation_id` bigint NOT NULL,
  `email` varchar(255) NOT NULL,
  `failed_count` int NOT NULL DEFAULT '0',
  `locked_until` timestamp(6) NULL DEFAULT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`attempt_key`),
  KEY `idx_login_attempts_org` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `marks_entries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `exam_schedule_id` bigint NOT NULL,
  `student_enrollment_id` bigint NOT NULL,
  `marks_obtained` double NOT NULL,
  `is_absent` tinyint(1) NOT NULL DEFAULT '0',
  `remarks` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `subject_rank` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_marks_entries_schedule_enrollment` (`exam_schedule_id`,`student_enrollment_id`),
  KEY `fk_marks_entries_organisation` (`organisation_id`),
  KEY `fk_marks_entries_enrollment` (`student_enrollment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `marks_entry_registers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `exam_schedule_id` bigint NOT NULL,
  `register_status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_marks_entry_registers_schedule` (`exam_schedule_id`),
  KEY `fk_marks_entry_registers_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `message_threads` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `type` varchar(20) NOT NULL,
  `section_id` bigint DEFAULT NULL,
  `last_message_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_message_threads_section_type` (`organisation_id`,`section_id`,`type`),
  KEY `fk_message_threads_section` (`section_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `thread_id` bigint NOT NULL,
  `sender_person_id` bigint NOT NULL,
  `body` text NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_messages_thread` (`thread_id`),
  KEY `fk_messages_sender` (`sender_person_id`),
  KEY `idx_messages_thread_created` (`organisation_id`,`thread_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notification_preferences` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `person_id` bigint NOT NULL,
  `channel` varchar(20) NOT NULL,
  `is_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification_preferences_person_channel` (`person_id`,`channel`),
  KEY `fk_notification_preferences_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notification_recipients` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `announcement_id` bigint DEFAULT NULL,
  `person_id` bigint NOT NULL,
  `channel` varchar(20) NOT NULL,
  `delivery_status` varchar(20) NOT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `failure_reason` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `subject` varchar(200) DEFAULT NULL,
  `body` text NOT NULL,
  `provider` varchar(20) DEFAULT NULL,
  `provider_message_id` varchar(255) DEFAULT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_notification_recipients_announcement` (`announcement_id`),
  KEY `fk_notification_recipients_person` (`person_id`),
  KEY `idx_notification_recipients_person_status` (`organisation_id`,`person_id`,`delivery_status`),
  KEY `idx_notification_recipients_status` (`organisation_id`,`delivery_status`),
  KEY `idx_notification_recipients_provider_message` (`provider`,`provider_message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notification_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `code` varchar(50) NOT NULL,
  `channel` varchar(20) NOT NULL,
  `subject_template` varchar(200) DEFAULT NULL,
  `body_template` text NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification_templates_org_code` (`organisation_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `organisation_branding` (
  `organisation_id` bigint NOT NULL,
  `logo_ref` varchar(255) DEFAULT NULL,
  `stamp_ref` varchar(255) DEFAULT NULL,
  `signature_ref` varchar(255) DEFAULT NULL,
  `school_name_override` varchar(255) DEFAULT NULL,
  `address_line` varchar(255) DEFAULT NULL,
  `affiliation_text` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `footer_text` varchar(500) DEFAULT NULL,
  `admission_number_format` varchar(100) NOT NULL DEFAULT 'STU-{YYYY}-{SEQ:4}',
  `invoice_number_format` varchar(100) NOT NULL DEFAULT 'INV-{AY}-{SEQ:6}',
  `receipt_number_format` varchar(100) NOT NULL DEFAULT 'RCT-{AY}-{SEQ:6}',
  PRIMARY KEY (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `organisation_configurations` (
  `organisation_id` bigint NOT NULL,
  `timezone` varchar(64) DEFAULT NULL,
  `default_currency` varchar(8) DEFAULT NULL,
  `date_format` varchar(32) DEFAULT NULL,
  `working_days_mask` int NOT NULL,
  `logo_url` varchar(512) DEFAULT NULL,
  `primary_color` varchar(16) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `primary_contact_name` varchar(200) DEFAULT NULL,
  `primary_contact_email` varchar(255) DEFAULT NULL,
  `primary_contact_phone` varchar(30) DEFAULT NULL,
  `address_line1` varchar(255) DEFAULT NULL,
  `address_line2` varchar(255) DEFAULT NULL,
  `city` varchar(100) DEFAULT NULL,
  `state` varchar(100) DEFAULT NULL,
  `pincode` varchar(20) DEFAULT NULL,
  `tax_identifier` varchar(100) DEFAULT NULL,
  `country` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `organisation_external_service_properties` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `external_service_id` bigint NOT NULL,
  `property_key` varchar(100) NOT NULL,
  `property_value` text,
  `is_secret` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_org_ext_service_props` (`organisation_id`,`external_service_id`,`property_key`),
  KEY `fk_org_ext_service_props_service` (`external_service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `organisation_memberships` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `person_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `campus_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `department_id` bigint DEFAULT NULL,
  `is_owner` tinyint(1) NOT NULL DEFAULT '0',
  `member_id` varchar(255) DEFAULT NULL,
  `joining_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_memberships_org_user_role_campus` (`organisation_id`,`user_id`,`role_id`,`campus_id`),
  KEY `fk_memberships_person` (`person_id`),
  KEY `fk_memberships_role` (`role_id`),
  KEY `fk_memberships_campus` (`campus_id`),
  KEY `idx_memberships_org_user` (`organisation_id`,`user_id`),
  KEY `idx_memberships_user` (`user_id`),
  KEY `fk_memberships_department` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `password_reset_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `token_hash` varchar(255) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `consumed` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_password_reset_tokens_user` (`user_id`),
  KEY `idx_password_reset_tokens_org_consumed` (`organisation_id`,`consumed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_allocations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `payment_id` bigint NOT NULL,
  `invoice_id` bigint NOT NULL,
  `allocated_amount` decimal(10,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_payment_allocations_payment_invoice` (`payment_id`,`invoice_id`),
  KEY `fk_payment_allocations_organisation` (`organisation_id`),
  KEY `idx_payment_allocations_invoice` (`invoice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_gateway_orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `invoice_id` bigint NOT NULL,
  `payment_id` bigint DEFAULT NULL,
  `link_token` varchar(64) NOT NULL,
  `gateway_order_id` varchar(100) DEFAULT NULL,
  `amount` decimal(10,2) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_payment_gateway_orders_link_token` (`link_token`),
  UNIQUE KEY `uq_payment_gateway_orders_gateway_order_id` (`gateway_order_id`),
  KEY `fk_pgo_organisation` (`organisation_id`),
  KEY `fk_pgo_invoice` (`invoice_id`),
  KEY `fk_pgo_payment` (`payment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `academic_year_id` bigint NOT NULL,
  `receipt_number` varchar(50) NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `payment_method` varchar(20) NOT NULL,
  `payment_date` date NOT NULL,
  `status` varchar(20) NOT NULL,
  `remarks` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `payment_reference` varchar(100) DEFAULT NULL,
  `payment_bank_name` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_payments_org_receipt_number` (`organisation_id`,`receipt_number`),
  KEY `fk_payments_academic_year` (`academic_year_id`),
  KEY `idx_payments_org_year_date` (`organisation_id`,`academic_year_id`,`payment_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payroll_runs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `pay_year` int NOT NULL,
  `pay_month` int NOT NULL,
  `status` varchar(20) NOT NULL,
  `supersedes_run_id` bigint DEFAULT NULL,
  `finalized_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_payroll_runs_supersedes` (`supersedes_run_id`),
  KEY `idx_payroll_runs_period` (`organisation_id`,`pay_year`,`pay_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payslip_lines` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `payslip_id` bigint NOT NULL,
  `label` varchar(200) NOT NULL,
  `line_type` varchar(20) NOT NULL,
  `source` varchar(20) NOT NULL,
  `amount` decimal(14,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_payslip_lines_organisation` (`organisation_id`),
  KEY `fk_payslip_lines_payslip` (`payslip_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payslips` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `payroll_run_id` bigint NOT NULL,
  `staff_profile_id` bigint NOT NULL,
  `employee_code` varchar(100) NOT NULL,
  `staff_name` varchar(250) NOT NULL,
  `designation_name` varchar(200) DEFAULT NULL,
  `department_name` varchar(200) DEFAULT NULL,
  `is_teacher` tinyint(1) NOT NULL DEFAULT '0',
  `basic_amount` decimal(14,2) NOT NULL,
  `gross_earnings` decimal(14,2) NOT NULL,
  `total_deductions` decimal(14,2) NOT NULL,
  `net_pay` decimal(14,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_payslips_run_staff` (`payroll_run_id`,`staff_profile_id`),
  KEY `fk_payslips_staff` (`staff_profile_id`),
  KEY `idx_payslips_staff` (`organisation_id`,`staff_profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `periods` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `ordinal` int NOT NULL,
  `label` varchar(100) NOT NULL,
  `start_time` time NOT NULL,
  `end_time` time NOT NULL,
  `is_break` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_periods_org_ordinal` (`organisation_id`,`ordinal`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(100) NOT NULL,
  `module` varchar(50) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_permissions_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `persons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `first_name` varchar(100) NOT NULL,
  `last_name` varchar(100) DEFAULT NULL,
  `date_of_birth` date DEFAULT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `photo_url` varchar(512) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `address` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_persons_org_user` (`organisation_id`,`user_id`),
  KEY `fk_persons_user` (`user_id`),
  KEY `idx_persons_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `portal_invites` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `person_id` bigint NOT NULL,
  `token_hash` varchar(255) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_portal_invites_person` (`person_id`),
  KEY `idx_portal_invites_org_status` (`organisation_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `profile_change_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `person_id` bigint NOT NULL,
  `phone` varchar(50) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `photo_url` varchar(500) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `requested_by` bigint NOT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_profile_change_requests_person` (`person_id`),
  KEY `idx_profile_change_requests_person_status` (`organisation_id`,`person_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `purchase_order_lines` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `purchase_order_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `unit_cost` decimal(10,2) NOT NULL,
  `quantity_received` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_purchase_order_lines_order` (`purchase_order_id`),
  KEY `fk_purchase_order_lines_item` (`item_id`),
  KEY `idx_purchase_order_lines_order` (`organisation_id`,`purchase_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `purchase_orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `supplier_id` bigint NOT NULL,
  `campus_id` bigint NOT NULL,
  `expected_date` date NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_purchase_orders_supplier` (`supplier_id`),
  KEY `fk_purchase_orders_campus` (`campus_id`),
  KEY `idx_purchase_orders_status` (`organisation_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `purchase_returns` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `purchase_order_line_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `reason` varchar(20) NOT NULL,
  `return_date` date NOT NULL,
  `remarks` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_purchase_returns_line` (`purchase_order_line_id`),
  KEY `idx_purchase_returns_line` (`organisation_id`,`purchase_order_line_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `quiz_attempt_answers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `attempt_id` bigint NOT NULL,
  `question_id` bigint NOT NULL,
  `selected_option_id` bigint DEFAULT NULL,
  `correct` tinyint(1) NOT NULL,
  `marks_awarded` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_quiz_attempt_answers` (`attempt_id`,`question_id`),
  KEY `fk_quiz_attempt_answers_organisation` (`organisation_id`),
  KEY `fk_quiz_attempt_answers_question` (`question_id`),
  KEY `fk_quiz_attempt_answers_option` (`selected_option_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `quiz_attempts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `quiz_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `submitted_by_person_id` bigint NOT NULL,
  `submitted_at` datetime(6) NOT NULL,
  `score` int NOT NULL,
  `max_score` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_quiz_attempts_student` (`quiz_id`,`student_id`),
  KEY `fk_quiz_attempts_student` (`student_id`),
  KEY `fk_quiz_attempts_by` (`submitted_by_person_id`),
  KEY `idx_quiz_attempts_student` (`organisation_id`,`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `quiz_options` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `question_id` bigint NOT NULL,
  `ordinal` int NOT NULL,
  `option_text` varchar(500) NOT NULL,
  `correct` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_quiz_options_organisation` (`organisation_id`),
  KEY `idx_quiz_options_question` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `quiz_questions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `quiz_id` bigint DEFAULT NULL,
  `subject_id` bigint NOT NULL,
  `ordinal` int NOT NULL,
  `question_text` varchar(2000) NOT NULL,
  `marks` int NOT NULL,
  `created_by_person_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_quiz_questions_quiz` (`quiz_id`),
  KEY `fk_quiz_questions_subject` (`subject_id`),
  KEY `fk_quiz_questions_author` (`created_by_person_id`),
  KEY `idx_quiz_questions_quiz` (`organisation_id`,`quiz_id`),
  KEY `idx_quiz_questions_bank` (`organisation_id`,`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `quizzes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `section_id` bigint NOT NULL,
  `subject_id` bigint NOT NULL,
  `title` varchar(250) NOT NULL,
  `instructions` varchar(2000) DEFAULT NULL,
  `opens_at` datetime(6) NOT NULL,
  `closes_at` datetime(6) NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_by_person_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_quizzes_section` (`section_id`),
  KEY `fk_quizzes_subject` (`subject_id`),
  KEY `fk_quizzes_author` (`created_by_person_id`),
  KEY `idx_quizzes_section` (`organisation_id`,`section_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `refresh_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `token_hash` varchar(255) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `revoked` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_refresh_tokens_user` (`user_id`),
  KEY `idx_refresh_tokens_org_revoked` (`organisation_id`,`revoked`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `refunds` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `payment_id` bigint NOT NULL,
  `invoice_id` bigint NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `approved_by` bigint NOT NULL,
  `refund_date` date NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `proof_file_reference` varchar(255) DEFAULT NULL,
  `proof_file_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_refunds_organisation` (`organisation_id`),
  KEY `idx_refunds_payment` (`payment_id`),
  KEY `idx_refunds_invoice` (`invoice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `regulatory_export_fields` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `schema_id` bigint NOT NULL,
  `ordinal` int NOT NULL,
  `column_header` varchar(200) NOT NULL,
  `source_field` varchar(100) NOT NULL,
  `default_value` varchar(200) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_reg_export_fields_ordinal` (`schema_id`,`ordinal`),
  KEY `fk_reg_export_fields_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `regulatory_export_schemas` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `code` varchar(60) NOT NULL,
  `name` varchar(200) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `entity_type` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_reg_export_schemas_code` (`organisation_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `report_cards` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `exam_id` bigint NOT NULL,
  `student_enrollment_id` bigint NOT NULL,
  `total_marks_obtained` double NOT NULL,
  `total_max_marks` double NOT NULL,
  `percentage` double NOT NULL,
  `overall_grade` varchar(10) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `passed` tinyint(1) NOT NULL DEFAULT '0',
  `class_rank` int DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PUBLISHED',
  `stale` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `fk_report_cards_organisation` (`organisation_id`),
  KEY `fk_report_cards_enrollment` (`student_enrollment_id`),
  KEY `idx_report_cards_exam_enrollment` (`exam_id`,`student_enrollment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `role_permissions` (
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`,`permission_id`),
  KEY `fk_role_permissions_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `is_system_default` tinyint(1) NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `is_managed` tinyint(1) NOT NULL DEFAULT '0',
  `is_portal` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_roles_org_name` (`organisation_id`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `route_stops` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `route_id` bigint NOT NULL,
  `stop_name` varchar(150) NOT NULL,
  `sequence_number` int NOT NULL,
  `pickup_time` time DEFAULT NULL,
  `drop_time` time DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_route_stops_route_sequence` (`route_id`,`sequence_number`),
  KEY `fk_route_stops_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `routes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `campus_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `code` varchar(30) NOT NULL,
  `vehicle_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_routes_org_campus_code` (`organisation_id`,`campus_id`,`code`),
  KEY `fk_routes_campus` (`campus_id`),
  KEY `fk_routes_vehicle` (`vehicle_id`),
  KEY `idx_routes_campus_status` (`organisation_id`,`campus_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `salary_components` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `component_type` varchar(20) NOT NULL,
  `calculation_basis` varchar(30) NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_salary_components_org_name` (`organisation_id`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `salary_structure_lines` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `salary_structure_id` bigint NOT NULL,
  `salary_component_id` bigint NOT NULL,
  `line_value` decimal(14,4) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_salary_structure_lines_component` (`salary_structure_id`,`salary_component_id`),
  KEY `fk_salary_structure_lines_organisation` (`organisation_id`),
  KEY `fk_salary_structure_lines_component` (`salary_component_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `salary_structures` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `staff_profile_id` bigint NOT NULL,
  `effective_from` date NOT NULL,
  `basic_amount` decimal(14,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_salary_structures_staff_effective` (`organisation_id`,`staff_profile_id`,`effective_from`),
  KEY `fk_salary_structures_staff` (`staff_profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sale_lines` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `sale_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `unit_price` decimal(10,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_sale_lines_sale` (`sale_id`),
  KEY `fk_sale_lines_item` (`item_id`),
  KEY `idx_sale_lines_sale` (`organisation_id`,`sale_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sale_payments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `sale_id` bigint NOT NULL,
  `payment_method` varchar(20) NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `paid_at` date NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_sale_payments_sale` (`sale_id`),
  KEY `idx_sale_payments_sale` (`organisation_id`,`sale_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sale_receipt_counters` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `next_number` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_sale_receipt_counters_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sales` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `customer_id` bigint NOT NULL,
  `campus_id` bigint NOT NULL,
  `receipt_number` varchar(50) NOT NULL,
  `sale_date` date NOT NULL,
  `total_amount` decimal(10,2) NOT NULL,
  `amount_paid` decimal(10,2) NOT NULL DEFAULT '0.00',
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_sales_customer` (`customer_id`),
  KEY `fk_sales_campus` (`campus_id`),
  KEY `idx_sales_customer` (`organisation_id`,`customer_id`),
  KEY `idx_sales_status` (`organisation_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `saved_report_columns` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `saved_report_id` bigint NOT NULL,
  `source_column` varchar(100) NOT NULL,
  `label` varchar(150) NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_saved_report_columns_report_column` (`saved_report_id`,`source_column`),
  KEY `fk_saved_report_columns_organisation` (`organisation_id`),
  KEY `idx_saved_report_columns_report` (`saved_report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `saved_report_parameters` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `saved_report_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `type` varchar(20) NOT NULL,
  `label` varchar(150) NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_saved_report_parameters_report_name` (`saved_report_id`,`name`),
  KEY `fk_saved_report_parameters_organisation` (`organisation_id`),
  KEY `idx_saved_report_parameters_report` (`saved_report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `saved_report_shares` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `saved_report_id` bigint NOT NULL,
  `principal_type` varchar(20) NOT NULL,
  `principal_id` bigint NOT NULL,
  `can_run` tinyint(1) NOT NULL DEFAULT '1',
  `can_edit` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_saved_report_shares_report_principal` (`saved_report_id`,`principal_type`,`principal_id`),
  KEY `fk_saved_report_shares_organisation` (`organisation_id`),
  KEY `idx_saved_report_shares_report` (`saved_report_id`),
  KEY `idx_saved_report_shares_principal` (`principal_type`,`principal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `saved_reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `name` varchar(150) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `sql_query` text NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_saved_reports_org_created_by` (`organisation_id`,`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `school_classes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `academic_year_id` bigint NOT NULL,
  `campus_id` bigint NOT NULL,
  `grade_level_id` bigint NOT NULL,
  `display_name` varchar(100) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_school_classes_year_campus_grade` (`academic_year_id`,`campus_id`,`grade_level_id`),
  KEY `fk_school_classes_campus` (`campus_id`),
  KEY `fk_school_classes_grade_level` (`grade_level_id`),
  KEY `idx_school_classes_organisation` (`organisation_id`),
  KEY `idx_school_classes_academic_year` (`academic_year_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `school_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `campus_id` bigint DEFAULT NULL,
  `event_date` date NOT NULL,
  `title` varchar(150) NOT NULL,
  `description` text,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_school_events_campus` (`campus_id`),
  KEY `idx_school_events_organisation` (`organisation_id`),
  KEY `idx_school_events_date` (`event_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `school_starter_needs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `need_key` varchar(40) NOT NULL,
  `label` varchar(200) NOT NULL,
  `help_text` varchar(500) DEFAULT NULL,
  `default_yes` tinyint(1) NOT NULL,
  `baseline` tinyint(1) NOT NULL,
  `display_order` int NOT NULL,
  `module_codes` varchar(200) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `need_key` (`need_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `school_template_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `category` varchar(20) NOT NULL,
  `name` varchar(150) NOT NULL,
  `code` varchar(50) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `sequence_order` int DEFAULT NULL,
  `stage` varchar(50) DEFAULT NULL,
  `category_type` varchar(20) DEFAULT NULL,
  `min_percentage` double DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `permission_codes` varchar(2000) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `module_code` varchar(40) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_school_template_items_org_category_name` (`organisation_id`,`category`,`name`),
  KEY `idx_school_template_items_org_category` (`organisation_id`,`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `school_template_master_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(20) NOT NULL,
  `name` varchar(150) NOT NULL,
  `code` varchar(50) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `sequence_order` int DEFAULT NULL,
  `stage` varchar(50) DEFAULT NULL,
  `category_type` varchar(20) DEFAULT NULL,
  `min_percentage` double DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `permission_codes` varchar(2000) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `module_code` varchar(40) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sections` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `school_class_id` bigint NOT NULL,
  `name` varchar(50) NOT NULL,
  `capacity` int DEFAULT NULL,
  `room` varchar(50) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_sections_class_name` (`school_class_id`,`name`),
  KEY `idx_sections_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `staff_assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `staff_profile_id` bigint NOT NULL,
  `campus_id` bigint DEFAULT NULL,
  `department_id` bigint DEFAULT NULL,
  `designation_id` bigint NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_staff_assignments_staff_profile` (`staff_profile_id`),
  KEY `fk_staff_assignments_campus` (`campus_id`),
  KEY `fk_staff_assignments_department` (`department_id`),
  KEY `fk_staff_assignments_designation` (`designation_id`),
  KEY `idx_staff_assignments_staff_status` (`organisation_id`,`staff_profile_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `staff_attendances` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `person_id` bigint NOT NULL,
  `campus_id` bigint NOT NULL,
  `attendance_date` date NOT NULL,
  `attendance_status` varchar(20) NOT NULL,
  `check_in_time` datetime(6) DEFAULT NULL,
  `check_out_time` datetime(6) DEFAULT NULL,
  `remarks` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_staff_attendances_person_date` (`person_id`,`attendance_date`),
  KEY `fk_staff_attendances_campus` (`campus_id`),
  KEY `idx_staff_attendances_campus_date` (`organisation_id`,`campus_id`,`attendance_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `staff_bank_details` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `staff_profile_id` bigint NOT NULL,
  `bank_account_holder_name` varchar(150) DEFAULT NULL,
  `bank_account_number` varchar(50) DEFAULT NULL,
  `bank_name` varchar(150) DEFAULT NULL,
  `bank_branch_code` varchar(30) DEFAULT NULL,
  `tax_identifier` varchar(100) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_staff_bank_details_staff_profile` (`staff_profile_id`),
  KEY `fk_staff_bank_details_organisation` (`organisation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `staff_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `staff_profile_id` bigint NOT NULL,
  `document_type` varchar(100) NOT NULL,
  `file_reference` varchar(500) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `mime_type` varchar(100) DEFAULT NULL,
  `verification_status` varchar(20) NOT NULL,
  `verified_by` bigint DEFAULT NULL,
  `verified_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_staff_documents_staff_profile` (`staff_profile_id`),
  KEY `idx_staff_documents_staff_status` (`organisation_id`,`staff_profile_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `staff_exits` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `staff_profile_id` bigint NOT NULL,
  `exit_type` varchar(20) NOT NULL,
  `exit_date` date NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `initiated_by` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_staff_exits_organisation` (`organisation_id`),
  KEY `fk_staff_exits_staff_profile` (`staff_profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `staff_invites` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `token_hash` varchar(255) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_staff_invites_user` (`user_id`),
  KEY `idx_staff_invites_org_status` (`organisation_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `staff_profiles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `person_id` bigint NOT NULL,
  `campus_id` bigint DEFAULT NULL,
  `employee_code` varchar(30) NOT NULL,
  `date_of_joining` date NOT NULL,
  `employment_type` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `designation_id` bigint NOT NULL,
  `department_id` bigint DEFAULT NULL,
  `reporting_manager_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_staff_profiles_org_employee_code` (`organisation_id`,`employee_code`),
  KEY `fk_staff_profiles_person` (`person_id`),
  KEY `fk_staff_profiles_campus` (`campus_id`),
  KEY `idx_staff_profiles_campus_status` (`organisation_id`,`campus_id`,`status`),
  KEY `fk_staff_profiles_designation` (`designation_id`),
  KEY `fk_staff_profiles_department` (`department_id`),
  KEY `fk_staff_profiles_reporting_manager` (`reporting_manager_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `stock_adjustments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `campus_id` bigint NOT NULL,
  `quantity_delta` int NOT NULL,
  `reason` varchar(20) NOT NULL,
  `adjustment_date` date NOT NULL,
  `remarks` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_stock_adjustments_item` (`item_id`),
  KEY `fk_stock_adjustments_campus` (`campus_id`),
  KEY `idx_stock_adjustments_item_campus` (`organisation_id`,`item_id`,`campus_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `stock_entries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `campus_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `unit_cost` decimal(10,2) DEFAULT NULL,
  `entry_date` date NOT NULL,
  `source` varchar(200) DEFAULT NULL,
  `remarks` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `supplier_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_stock_entries_item` (`item_id`),
  KEY `fk_stock_entries_campus` (`campus_id`),
  KEY `idx_stock_entries_item_campus` (`organisation_id`,`item_id`,`campus_id`),
  KEY `fk_stock_entries_supplier` (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `stock_issues` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `campus_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `issued_date` date NOT NULL,
  `issued_to` varchar(200) NOT NULL,
  `purpose` varchar(500) DEFAULT NULL,
  `remarks` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `unit_cost` decimal(10,2) DEFAULT NULL,
  `department_id` bigint DEFAULT NULL,
  `recipient_person_id` bigint DEFAULT NULL,
  `recipient_student_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_stock_issues_item` (`item_id`),
  KEY `fk_stock_issues_campus` (`campus_id`),
  KEY `idx_stock_issues_item_campus` (`organisation_id`,`item_id`,`campus_id`),
  KEY `fk_stock_issues_department` (`department_id`),
  KEY `fk_stock_issues_recipient_person` (`recipient_person_id`),
  KEY `fk_stock_issues_recipient_student` (`recipient_student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `stock_transfers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `from_campus_id` bigint NOT NULL,
  `to_campus_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `transfer_date` date NOT NULL,
  `remarks` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_stock_transfers_item` (`item_id`),
  KEY `fk_stock_transfers_from_campus` (`from_campus_id`),
  KEY `fk_stock_transfers_to_campus` (`to_campus_id`),
  KEY `idx_stock_transfers_item` (`organisation_id`,`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_admission_counters` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `calendar_year` int NOT NULL,
  `next_number` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_student_admission_counters_year` (`organisation_id`,`calendar_year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_attendances` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `student_enrollment_id` bigint NOT NULL,
  `academic_year_id` bigint NOT NULL,
  `school_class_id` bigint NOT NULL,
  `section_id` bigint NOT NULL,
  `attendance_date` date NOT NULL,
  `attendance_status` varchar(20) NOT NULL,
  `is_excused` tinyint(1) NOT NULL DEFAULT '0',
  `remarks` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_student_attendances_enrollment_date` (`student_enrollment_id`,`attendance_date`),
  KEY `fk_student_attendances_academic_year` (`academic_year_id`),
  KEY `fk_student_attendances_school_class` (`school_class_id`),
  KEY `fk_student_attendances_section` (`section_id`),
  KEY `idx_student_attendances_class_section_date` (`organisation_id`,`school_class_id`,`section_id`,`attendance_date`),
  KEY `idx_student_attendances_org_year_date` (`organisation_id`,`academic_year_id`,`attendance_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `document_type` varchar(50) NOT NULL,
  `file_reference` varchar(512) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `mime_type` varchar(100) DEFAULT NULL,
  `verification_status` varchar(20) NOT NULL,
  `verified_by` bigint DEFAULT NULL,
  `verified_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_student_documents_organisation` (`organisation_id`),
  KEY `idx_student_documents_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_enrollments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `academic_year_id` bigint NOT NULL,
  `section_id` bigint NOT NULL,
  `roll_number` int DEFAULT NULL,
  `enrollment_status` varchar(20) NOT NULL,
  `is_current` tinyint(1) NOT NULL DEFAULT '1',
  `enrolled_date` date NOT NULL,
  `exit_date` date DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_student_enrollments_student_year` (`student_id`,`academic_year_id`),
  UNIQUE KEY `uq_student_enrollments_section_roll` (`section_id`,`roll_number`),
  KEY `fk_student_enrollments_academic_year` (`academic_year_id`),
  KEY `idx_student_enrollments_student_current` (`student_id`,`is_current`),
  KEY `idx_student_enrollments_section` (`section_id`),
  KEY `idx_student_enrollments_org_year` (`organisation_id`,`academic_year_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_exits` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `exit_type` varchar(20) NOT NULL,
  `exit_date` date NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `destination_school` varchar(255) DEFAULT NULL,
  `initiated_by` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_student_exits_organisation` (`organisation_id`),
  KEY `idx_student_exits_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_fee_assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `student_enrollment_id` bigint NOT NULL,
  `fee_structure_id` bigint NOT NULL,
  `base_amount` decimal(10,2) NOT NULL,
  `discount_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `effective_amount` decimal(10,2) NOT NULL,
  `discount_reason` varchar(500) DEFAULT NULL,
  `approved_by` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `payment_frequency` varchar(20) DEFAULT NULL,
  `proof_file_reference` varchar(255) DEFAULT NULL,
  `proof_file_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_student_fee_assignments_organisation` (`organisation_id`),
  KEY `fk_student_fee_assignments_structure` (`fee_structure_id`),
  KEY `idx_student_fee_assignments_enrollment` (`student_enrollment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_guardians` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `guardian_id` bigint NOT NULL,
  `relationship_type` varchar(30) NOT NULL,
  `is_primary_guardian` tinyint(1) NOT NULL DEFAULT '0',
  `is_emergency_contact` tinyint(1) NOT NULL DEFAULT '0',
  `can_pickup` tinyint(1) NOT NULL DEFAULT '0',
  `can_receive_communication` tinyint(1) NOT NULL DEFAULT '1',
  `contact_priority` int NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_student_guardians_student_guardian` (`student_id`,`guardian_id`),
  KEY `fk_student_guardians_organisation` (`organisation_id`),
  KEY `idx_student_guardians_student` (`student_id`),
  KEY `idx_student_guardians_guardian` (`guardian_id`),
  KEY `idx_student_guardians_student_priority` (`student_id`,`contact_priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_id_counters` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `counter_year` int NOT NULL,
  `next_number` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_student_id_counters_org_year` (`organisation_id`,`counter_year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_rewards` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `title` varchar(200) NOT NULL,
  `points` int NOT NULL,
  `reason` varchar(1000) DEFAULT NULL,
  `granted_by_person_id` bigint NOT NULL,
  `granted_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_student_rewards_student` (`student_id`),
  KEY `fk_student_rewards_granter` (`granted_by_person_id`),
  KEY `idx_student_rewards_student` (`organisation_id`,`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_transfer_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `from_campus_id` bigint NOT NULL,
  `to_campus_id` bigint NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `requested_by` bigint NOT NULL,
  `decided_by` bigint DEFAULT NULL,
  `decided_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_student_transfer_requests_student` (`student_id`),
  KEY `fk_student_transfer_requests_from_campus` (`from_campus_id`),
  KEY `fk_student_transfer_requests_to_campus` (`to_campus_id`),
  KEY `idx_student_transfer_requests_student_status` (`organisation_id`,`student_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_transport_assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `student_enrollment_id` bigint NOT NULL,
  `route_id` bigint NOT NULL,
  `route_stop_id` bigint NOT NULL,
  `uses_pickup` tinyint(1) NOT NULL,
  `uses_drop` tinyint(1) NOT NULL,
  `status` varchar(20) NOT NULL,
  `effective_from` date NOT NULL,
  `effective_to` date DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `student_fee_assignment_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_transport_assignments_enrollment` (`student_enrollment_id`),
  KEY `fk_transport_assignments_route` (`route_id`),
  KEY `fk_transport_assignments_route_stop` (`route_stop_id`),
  KEY `idx_transport_assignments_enrollment_status` (`organisation_id`,`student_enrollment_id`,`status`),
  KEY `idx_transport_assignments_route_stop` (`organisation_id`,`route_stop_id`,`status`),
  KEY `fk_transport_assignments_fee_assignment` (`student_fee_assignment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_trip_attendances` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `trip_log_id` bigint NOT NULL,
  `student_enrollment_id` bigint NOT NULL,
  `boarding_status` varchar(20) NOT NULL,
  `remarks` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_trip_attendance_trip_enrollment` (`trip_log_id`,`student_enrollment_id`),
  KEY `fk_trip_attendance_enrollment` (`student_enrollment_id`),
  KEY `idx_trip_attendance_trip` (`organisation_id`,`trip_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `students` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `person_id` bigint NOT NULL,
  `admission_number` varchar(50) NOT NULL,
  `admission_date` date NOT NULL,
  `admission_source` varchar(50) DEFAULT NULL,
  `previous_school` varchar(255) DEFAULT NULL,
  `tc_number` varchar(50) DEFAULT NULL,
  `entrance_score` double DEFAULT NULL,
  `blood_group` varchar(10) DEFAULT NULL,
  `category` varchar(50) DEFAULT NULL,
  `nationality` varchar(50) DEFAULT NULL,
  `remarks` varchar(500) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `student_id` varchar(30) NOT NULL,
  `medical_alerts` text,
  `emergency_contact_name` varchar(150) DEFAULT NULL,
  `emergency_contact_phone` varchar(30) DEFAULT NULL,
  `custom_fields` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_students_person` (`person_id`),
  UNIQUE KEY `uq_students_org_admission_number` (`organisation_id`,`admission_number`),
  UNIQUE KEY `uq_students_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `subjects` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `code` varchar(20) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_subjects_org_name` (`organisation_id`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `suppliers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `name` varchar(200) NOT NULL,
  `contact_person` varchar(200) DEFAULT NULL,
  `phone` varchar(30) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `address` varchar(500) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_suppliers_org_status` (`organisation_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `teacher_assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `academic_year_id` bigint NOT NULL,
  `section_id` bigint NOT NULL,
  `subject_id` bigint DEFAULT NULL,
  `teacher_person_id` bigint NOT NULL,
  `assignment_type` varchar(20) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_teacher_assignments_organisation` (`organisation_id`),
  KEY `fk_teacher_assignments_subject` (`subject_id`),
  KEY `idx_teacher_assignments_section_status` (`section_id`,`status`),
  KEY `idx_teacher_assignments_teacher_status` (`teacher_person_id`,`status`),
  KEY `idx_teacher_assignments_academic_year` (`academic_year_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `thread_participants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `thread_id` bigint NOT NULL,
  `person_id` bigint NOT NULL,
  `last_read_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_thread_participants_thread_person` (`thread_id`,`person_id`),
  KEY `fk_thread_participants_person` (`person_id`),
  KEY `idx_thread_participants_person` (`organisation_id`,`person_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `timetable_entries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `academic_year_id` bigint NOT NULL,
  `section_id` bigint NOT NULL,
  `subject_id` bigint NOT NULL,
  `teacher_person_id` bigint NOT NULL,
  `day_of_week` varchar(10) NOT NULL,
  `period_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_timetable_section_slot` (`organisation_id`,`section_id`,`day_of_week`,`period_id`),
  UNIQUE KEY `uq_timetable_teacher_slot` (`organisation_id`,`academic_year_id`,`teacher_person_id`,`day_of_week`,`period_id`),
  KEY `fk_timetable_academic_year` (`academic_year_id`),
  KEY `fk_timetable_section` (`section_id`),
  KEY `fk_timetable_subject` (`subject_id`),
  KEY `fk_timetable_teacher` (`teacher_person_id`),
  KEY `fk_timetable_period` (`period_id`),
  KEY `idx_timetable_teacher` (`organisation_id`,`teacher_person_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `transfer_connector_jobs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `student_exit_id` bigint NOT NULL,
  `schema_id` bigint NOT NULL,
  `connector_name` varchar(100) NOT NULL,
  `payload` longtext NOT NULL,
  `status` varchar(20) NOT NULL,
  `external_reference` varchar(200) DEFAULT NULL,
  `failure_reason` varchar(1000) DEFAULT NULL,
  `prepared_by` bigint DEFAULT NULL,
  `submitted_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_transfer_jobs_exit` (`student_exit_id`),
  KEY `fk_transfer_jobs_schema` (`schema_id`),
  KEY `idx_transfer_jobs_exit` (`organisation_id`,`student_exit_id`),
  KEY `idx_transfer_jobs_status` (`organisation_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `trip_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `route_id` bigint NOT NULL,
  `vehicle_id` bigint NOT NULL,
  `driver_person_id` bigint DEFAULT NULL,
  `trip_date` date NOT NULL,
  `trip_type` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `remarks` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_trip_logs_route` (`route_id`),
  KEY `fk_trip_logs_vehicle` (`vehicle_id`),
  KEY `fk_trip_logs_driver` (`driver_person_id`),
  KEY `idx_trip_logs_route_date` (`organisation_id`,`route_id`,`trip_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `email` varchar(255) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `password_hash` varchar(255) NOT NULL,
  `status` varchar(20) NOT NULL,
  `last_login_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `email_verified_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_users_org_email` (`organisation_id`,`email`),
  UNIQUE KEY `uq_users_org_phone` (`organisation_id`,`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `vehicle_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `vehicle_id` bigint NOT NULL,
  `document_type` varchar(50) NOT NULL,
  `file_reference` varchar(500) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `mime_type` varchar(100) DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `verification_status` varchar(20) NOT NULL,
  `verified_by` bigint DEFAULT NULL,
  `verified_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_vehicle_documents_vehicle` (`vehicle_id`),
  KEY `idx_vehicle_documents_vehicle_status` (`organisation_id`,`vehicle_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `vehicles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `campus_id` bigint NOT NULL,
  `registration_number` varchar(30) NOT NULL,
  `vehicle_type` varchar(20) NOT NULL,
  `capacity` int NOT NULL,
  `driver_person_id` bigint DEFAULT NULL,
  `attendant_person_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_vehicles_org_registration` (`organisation_id`,`registration_number`),
  KEY `fk_vehicles_campus` (`campus_id`),
  KEY `fk_vehicles_driver` (`driver_person_id`),
  KEY `fk_vehicles_attendant` (`attendant_person_id`),
  KEY `idx_vehicles_campus_status` (`organisation_id`,`campus_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `waivers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `invoice_id` bigint NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `approved_by` bigint NOT NULL,
  `waiver_date` date NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `proof_file_reference` varchar(255) DEFAULT NULL,
  `proof_file_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_waivers_organisation` (`organisation_id`),
  KEY `idx_waivers_invoice` (`invoice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `working_calendar_entries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organisation_id` bigint NOT NULL,
  `academic_year_id` bigint NOT NULL,
  `campus_id` bigint DEFAULT NULL,
  `entry_date` date NOT NULL,
  `label` varchar(150) NOT NULL,
  `type` varchar(30) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_working_calendar_entries_campus` (`campus_id`),
  KEY `idx_working_calendar_entries_organisation` (`organisation_id`),
  KEY `idx_working_calendar_entries_academic_year` (`academic_year_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `adjustments` ADD CONSTRAINT `fk_adjustments_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`) ;
ALTER TABLE `announcement_audience_members` ADD CONSTRAINT `fk_announcement_audience_members_announcement` FOREIGN KEY (`announcement_id`) REFERENCES `announcements` (`id`) ;
ALTER TABLE `announcement_audience_members` ADD CONSTRAINT `fk_announcement_audience_members_person` FOREIGN KEY (`person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `announcements` ADD CONSTRAINT `fk_announcements_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `answer_sheet_annotations` ADD CONSTRAINT `fk_answer_sheet_annotations_page` FOREIGN KEY (`page_id`) REFERENCES `answer_sheet_pages` (`id`) ;
ALTER TABLE `answer_sheet_pages` ADD CONSTRAINT `fk_answer_sheet_pages_sheet` FOREIGN KEY (`sheet_id`) REFERENCES `answer_sheets` (`id`) ;
ALTER TABLE `answer_sheets` ADD CONSTRAINT `fk_answer_sheets_enrollment` FOREIGN KEY (`student_enrollment_id`) REFERENCES `student_enrollments` (`id`) ;
ALTER TABLE `answer_sheets` ADD CONSTRAINT `fk_answer_sheets_marker` FOREIGN KEY (`marked_by_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `answer_sheets` ADD CONSTRAINT `fk_answer_sheets_schedule` FOREIGN KEY (`exam_schedule_id`) REFERENCES `exam_schedules` (`id`) ;
ALTER TABLE `answer_sheets` ADD CONSTRAINT `fk_answer_sheets_uploader` FOREIGN KEY (`uploaded_by_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `applicant_documents` ADD CONSTRAINT `fk_applicant_documents_applicant` FOREIGN KEY (`applicant_id`) REFERENCES `applicants` (`id`) ;
ALTER TABLE `applicants` ADD CONSTRAINT `fk_applicants_desired_grade` FOREIGN KEY (`desired_grade_id`) REFERENCES `grade_levels` (`id`) ;
ALTER TABLE `applicants` ADD CONSTRAINT `fk_applicants_person` FOREIGN KEY (`person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `assets` ADD CONSTRAINT `fk_assets_assigned_person` FOREIGN KEY (`assigned_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `assets` ADD CONSTRAINT `fk_assets_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `assets` ADD CONSTRAINT `fk_assets_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`) ;
ALTER TABLE `attendance_corrections` ADD CONSTRAINT `fk_attendance_corrections_student_attendance` FOREIGN KEY (`student_attendance_id`) REFERENCES `student_attendances` (`id`) ;
ALTER TABLE `book_copies` ADD CONSTRAINT `fk_book_copies_book` FOREIGN KEY (`book_id`) REFERENCES `books` (`id`) ;
ALTER TABLE `book_copies` ADD CONSTRAINT `fk_book_copies_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `borrow_records` ADD CONSTRAINT `fk_borrow_records_book_copy` FOREIGN KEY (`book_copy_id`) REFERENCES `book_copies` (`id`) ;
ALTER TABLE `borrow_records` ADD CONSTRAINT `fk_borrow_records_borrower` FOREIGN KEY (`borrower_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `class_attendance_registers` ADD CONSTRAINT `fk_class_attendance_registers_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`id`) ;
ALTER TABLE `class_attendance_registers` ADD CONSTRAINT `fk_class_attendance_registers_section` FOREIGN KEY (`section_id`) REFERENCES `sections` (`id`) ;
ALTER TABLE `class_subjects` ADD CONSTRAINT `fk_class_subjects_school_class` FOREIGN KEY (`school_class_id`) REFERENCES `school_classes` (`id`) ;
ALTER TABLE `class_subjects` ADD CONSTRAINT `fk_class_subjects_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`) ;
ALTER TABLE `consent_records` ADD CONSTRAINT `fk_consent_records_person` FOREIGN KEY (`person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `consent_records` ADD CONSTRAINT `fk_consent_records_purpose` FOREIGN KEY (`purpose_id`) REFERENCES `consent_purposes` (`id`) ;
ALTER TABLE `customers` ADD CONSTRAINT `fk_customers_guardian` FOREIGN KEY (`guardian_id`) REFERENCES `guardians` (`id`) ;
ALTER TABLE `customers` ADD CONSTRAINT `fk_customers_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`) ;
ALTER TABLE `data_subject_requests` ADD CONSTRAINT `fk_dsr_person` FOREIGN KEY (`person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `driver_profiles` ADD CONSTRAINT `fk_driver_profiles_person` FOREIGN KEY (`person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `email_verification_tokens` ADD CONSTRAINT `fk_email_verification_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ;
ALTER TABLE `exam_schedules` ADD CONSTRAINT `fk_exam_schedules_exam` FOREIGN KEY (`exam_id`) REFERENCES `exams` (`id`) ;
ALTER TABLE `exam_schedules` ADD CONSTRAINT `fk_exam_schedules_school_class` FOREIGN KEY (`school_class_id`) REFERENCES `school_classes` (`id`) ;
ALTER TABLE `exam_schedules` ADD CONSTRAINT `fk_exam_schedules_section` FOREIGN KEY (`section_id`) REFERENCES `sections` (`id`) ;
ALTER TABLE `exam_schedules` ADD CONSTRAINT `fk_exam_schedules_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`) ;
ALTER TABLE `exams` ADD CONSTRAINT `fk_exams_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`id`) ;
ALTER TABLE `fee_document_counters` ADD CONSTRAINT `fk_fee_document_counters_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`id`) ;
ALTER TABLE `fee_structure_groups` ADD CONSTRAINT `fk_fee_structure_groups_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`id`) ;
ALTER TABLE `fee_structure_groups` ADD CONSTRAINT `fk_fee_structure_groups_school_class` FOREIGN KEY (`school_class_id`) REFERENCES `school_classes` (`id`) ;
ALTER TABLE `fee_structure_installments` ADD CONSTRAINT `fk_fee_structure_installments_structure` FOREIGN KEY (`fee_structure_id`) REFERENCES `fee_structures` (`id`) ;
ALTER TABLE `fee_structures` ADD CONSTRAINT `fk_fee_structures_fee_category` FOREIGN KEY (`fee_category_id`) REFERENCES `fee_categories` (`id`) ;
ALTER TABLE `fee_structures` ADD CONSTRAINT `fk_fee_structures_group` FOREIGN KEY (`fee_structure_group_id`) REFERENCES `fee_structure_groups` (`id`) ;
ALTER TABLE `fines` ADD CONSTRAINT `fk_fines_borrow_record` FOREIGN KEY (`borrow_record_id`) REFERENCES `borrow_records` (`id`) ;
ALTER TABLE `grading_scale_bands` ADD CONSTRAINT `fk_grading_scale_bands_scale` FOREIGN KEY (`grading_scale_id`) REFERENCES `grading_scales` (`id`) ;
ALTER TABLE `guardians` ADD CONSTRAINT `fk_guardians_person` FOREIGN KEY (`person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `homework` ADD CONSTRAINT `fk_homework_assigned_by` FOREIGN KEY (`assigned_by_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `homework` ADD CONSTRAINT `fk_homework_section` FOREIGN KEY (`section_id`) REFERENCES `sections` (`id`) ;
ALTER TABLE `homework` ADD CONSTRAINT `fk_homework_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`) ;
ALTER TABLE `homework_submissions` ADD CONSTRAINT `fk_homework_submissions_by` FOREIGN KEY (`submitted_by_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `homework_submissions` ADD CONSTRAINT `fk_homework_submissions_homework` FOREIGN KEY (`homework_id`) REFERENCES `homework` (`id`) ;
ALTER TABLE `homework_submissions` ADD CONSTRAINT `fk_homework_submissions_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`) ;
ALTER TABLE `invoices` ADD CONSTRAINT `fk_invoices_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`id`) ;
ALTER TABLE `invoices` ADD CONSTRAINT `fk_invoices_assignment` FOREIGN KEY (`student_fee_assignment_id`) REFERENCES `student_fee_assignments` (`id`) ;
ALTER TABLE `invoices` ADD CONSTRAINT `fk_invoices_installment` FOREIGN KEY (`fee_structure_installment_id`) REFERENCES `fee_structure_installments` (`id`) ;
ALTER TABLE `items` ADD CONSTRAINT `fk_items_category` FOREIGN KEY (`item_category_id`) REFERENCES `item_categories` (`id`) ;
ALTER TABLE `items` ADD CONSTRAINT `fk_items_default_supplier` FOREIGN KEY (`default_supplier_id`) REFERENCES `suppliers` (`id`) ;
ALTER TABLE `learning_materials` ADD CONSTRAINT `fk_learning_materials_class` FOREIGN KEY (`school_class_id`) REFERENCES `school_classes` (`id`) ;
ALTER TABLE `learning_materials` ADD CONSTRAINT `fk_learning_materials_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`) ;
ALTER TABLE `learning_materials` ADD CONSTRAINT `fk_learning_materials_uploaded_by` FOREIGN KEY (`uploaded_by_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `leave_balances` ADD CONSTRAINT `fk_leave_balances_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`id`) ;
ALTER TABLE `leave_balances` ADD CONSTRAINT `fk_leave_balances_leave_type` FOREIGN KEY (`leave_type_id`) REFERENCES `leave_types` (`id`) ;
ALTER TABLE `leave_balances` ADD CONSTRAINT `fk_leave_balances_staff_profile` FOREIGN KEY (`staff_profile_id`) REFERENCES `staff_profiles` (`id`) ;
ALTER TABLE `leave_requests` ADD CONSTRAINT `fk_leave_requests_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`id`) ;
ALTER TABLE `leave_requests` ADD CONSTRAINT `fk_leave_requests_leave_type` FOREIGN KEY (`leave_type_id`) REFERENCES `leave_types` (`id`) ;
ALTER TABLE `leave_requests` ADD CONSTRAINT `fk_leave_requests_staff_profile` FOREIGN KEY (`staff_profile_id`) REFERENCES `staff_profiles` (`id`) ;
ALTER TABLE `lesson_plans` ADD CONSTRAINT `fk_lesson_plans_author` FOREIGN KEY (`submitted_by_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `lesson_plans` ADD CONSTRAINT `fk_lesson_plans_reviewer` FOREIGN KEY (`reviewed_by_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `lesson_plans` ADD CONSTRAINT `fk_lesson_plans_section` FOREIGN KEY (`section_id`) REFERENCES `sections` (`id`) ;
ALTER TABLE `lesson_plans` ADD CONSTRAINT `fk_lesson_plans_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`) ;
ALTER TABLE `marks_entries` ADD CONSTRAINT `fk_marks_entries_enrollment` FOREIGN KEY (`student_enrollment_id`) REFERENCES `student_enrollments` (`id`) ;
ALTER TABLE `marks_entries` ADD CONSTRAINT `fk_marks_entries_schedule` FOREIGN KEY (`exam_schedule_id`) REFERENCES `exam_schedules` (`id`) ;
ALTER TABLE `marks_entry_registers` ADD CONSTRAINT `fk_marks_entry_registers_schedule` FOREIGN KEY (`exam_schedule_id`) REFERENCES `exam_schedules` (`id`) ;
ALTER TABLE `message_threads` ADD CONSTRAINT `fk_message_threads_section` FOREIGN KEY (`section_id`) REFERENCES `sections` (`id`) ;
ALTER TABLE `messages` ADD CONSTRAINT `fk_messages_sender` FOREIGN KEY (`sender_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `messages` ADD CONSTRAINT `fk_messages_thread` FOREIGN KEY (`thread_id`) REFERENCES `message_threads` (`id`) ;
ALTER TABLE `notification_preferences` ADD CONSTRAINT `fk_notification_preferences_person` FOREIGN KEY (`person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `notification_recipients` ADD CONSTRAINT `fk_notification_recipients_announcement` FOREIGN KEY (`announcement_id`) REFERENCES `announcements` (`id`) ;
ALTER TABLE `notification_recipients` ADD CONSTRAINT `fk_notification_recipients_person` FOREIGN KEY (`person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `organisation_memberships` ADD CONSTRAINT `fk_memberships_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `organisation_memberships` ADD CONSTRAINT `fk_memberships_department` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`) ;
ALTER TABLE `organisation_memberships` ADD CONSTRAINT `fk_memberships_person` FOREIGN KEY (`person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `organisation_memberships` ADD CONSTRAINT `fk_memberships_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ;
ALTER TABLE `organisation_memberships` ADD CONSTRAINT `fk_memberships_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ;
ALTER TABLE `password_reset_tokens` ADD CONSTRAINT `fk_password_reset_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ;
ALTER TABLE `payment_allocations` ADD CONSTRAINT `fk_payment_allocations_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`) ;
ALTER TABLE `payment_allocations` ADD CONSTRAINT `fk_payment_allocations_payment` FOREIGN KEY (`payment_id`) REFERENCES `payments` (`id`) ;
ALTER TABLE `payment_gateway_orders` ADD CONSTRAINT `fk_pgo_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`) ;
ALTER TABLE `payment_gateway_orders` ADD CONSTRAINT `fk_pgo_payment` FOREIGN KEY (`payment_id`) REFERENCES `payments` (`id`) ;
ALTER TABLE `payments` ADD CONSTRAINT `fk_payments_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`id`) ;
ALTER TABLE `payroll_runs` ADD CONSTRAINT `fk_payroll_runs_supersedes` FOREIGN KEY (`supersedes_run_id`) REFERENCES `payroll_runs` (`id`) ;
ALTER TABLE `payslip_lines` ADD CONSTRAINT `fk_payslip_lines_payslip` FOREIGN KEY (`payslip_id`) REFERENCES `payslips` (`id`) ;
ALTER TABLE `payslips` ADD CONSTRAINT `fk_payslips_run` FOREIGN KEY (`payroll_run_id`) REFERENCES `payroll_runs` (`id`) ;
ALTER TABLE `payslips` ADD CONSTRAINT `fk_payslips_staff` FOREIGN KEY (`staff_profile_id`) REFERENCES `staff_profiles` (`id`) ;
ALTER TABLE `persons` ADD CONSTRAINT `fk_persons_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ;
ALTER TABLE `portal_invites` ADD CONSTRAINT `fk_portal_invites_person` FOREIGN KEY (`person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `profile_change_requests` ADD CONSTRAINT `fk_profile_change_requests_person` FOREIGN KEY (`person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `purchase_order_lines` ADD CONSTRAINT `fk_purchase_order_lines_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`) ;
ALTER TABLE `purchase_order_lines` ADD CONSTRAINT `fk_purchase_order_lines_order` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_orders` (`id`) ;
ALTER TABLE `purchase_orders` ADD CONSTRAINT `fk_purchase_orders_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `purchase_orders` ADD CONSTRAINT `fk_purchase_orders_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`) ;
ALTER TABLE `purchase_returns` ADD CONSTRAINT `fk_purchase_returns_line` FOREIGN KEY (`purchase_order_line_id`) REFERENCES `purchase_order_lines` (`id`) ;
ALTER TABLE `quiz_attempt_answers` ADD CONSTRAINT `fk_quiz_attempt_answers_attempt` FOREIGN KEY (`attempt_id`) REFERENCES `quiz_attempts` (`id`) ;
ALTER TABLE `quiz_attempt_answers` ADD CONSTRAINT `fk_quiz_attempt_answers_option` FOREIGN KEY (`selected_option_id`) REFERENCES `quiz_options` (`id`) ;
ALTER TABLE `quiz_attempt_answers` ADD CONSTRAINT `fk_quiz_attempt_answers_question` FOREIGN KEY (`question_id`) REFERENCES `quiz_questions` (`id`) ;
ALTER TABLE `quiz_attempts` ADD CONSTRAINT `fk_quiz_attempts_by` FOREIGN KEY (`submitted_by_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `quiz_attempts` ADD CONSTRAINT `fk_quiz_attempts_quiz` FOREIGN KEY (`quiz_id`) REFERENCES `quizzes` (`id`) ;
ALTER TABLE `quiz_attempts` ADD CONSTRAINT `fk_quiz_attempts_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`) ;
ALTER TABLE `quiz_options` ADD CONSTRAINT `fk_quiz_options_question` FOREIGN KEY (`question_id`) REFERENCES `quiz_questions` (`id`) ;
ALTER TABLE `quiz_questions` ADD CONSTRAINT `fk_quiz_questions_author` FOREIGN KEY (`created_by_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `quiz_questions` ADD CONSTRAINT `fk_quiz_questions_quiz` FOREIGN KEY (`quiz_id`) REFERENCES `quizzes` (`id`) ;
ALTER TABLE `quiz_questions` ADD CONSTRAINT `fk_quiz_questions_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`) ;
ALTER TABLE `quizzes` ADD CONSTRAINT `fk_quizzes_author` FOREIGN KEY (`created_by_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `quizzes` ADD CONSTRAINT `fk_quizzes_section` FOREIGN KEY (`section_id`) REFERENCES `sections` (`id`) ;
ALTER TABLE `quizzes` ADD CONSTRAINT `fk_quizzes_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`) ;
ALTER TABLE `refresh_tokens` ADD CONSTRAINT `fk_refresh_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ;
ALTER TABLE `refunds` ADD CONSTRAINT `fk_refunds_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`) ;
ALTER TABLE `refunds` ADD CONSTRAINT `fk_refunds_payment` FOREIGN KEY (`payment_id`) REFERENCES `payments` (`id`) ;
ALTER TABLE `regulatory_export_fields` ADD CONSTRAINT `fk_reg_export_fields_schema` FOREIGN KEY (`schema_id`) REFERENCES `regulatory_export_schemas` (`id`) ;
ALTER TABLE `report_cards` ADD CONSTRAINT `fk_report_cards_enrollment` FOREIGN KEY (`student_enrollment_id`) REFERENCES `student_enrollments` (`id`) ;
ALTER TABLE `report_cards` ADD CONSTRAINT `fk_report_cards_exam` FOREIGN KEY (`exam_id`) REFERENCES `exams` (`id`) ;
ALTER TABLE `role_permissions` ADD CONSTRAINT `fk_role_permissions_permission` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`) ;
ALTER TABLE `role_permissions` ADD CONSTRAINT `fk_role_permissions_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ;
ALTER TABLE `route_stops` ADD CONSTRAINT `fk_route_stops_route` FOREIGN KEY (`route_id`) REFERENCES `routes` (`id`) ;
ALTER TABLE `routes` ADD CONSTRAINT `fk_routes_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `routes` ADD CONSTRAINT `fk_routes_vehicle` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`) ;
ALTER TABLE `salary_structure_lines` ADD CONSTRAINT `fk_salary_structure_lines_component` FOREIGN KEY (`salary_component_id`) REFERENCES `salary_components` (`id`) ;
ALTER TABLE `salary_structure_lines` ADD CONSTRAINT `fk_salary_structure_lines_structure` FOREIGN KEY (`salary_structure_id`) REFERENCES `salary_structures` (`id`) ;
ALTER TABLE `salary_structures` ADD CONSTRAINT `fk_salary_structures_staff` FOREIGN KEY (`staff_profile_id`) REFERENCES `staff_profiles` (`id`) ;
ALTER TABLE `sale_lines` ADD CONSTRAINT `fk_sale_lines_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`) ;
ALTER TABLE `sale_lines` ADD CONSTRAINT `fk_sale_lines_sale` FOREIGN KEY (`sale_id`) REFERENCES `sales` (`id`) ;
ALTER TABLE `sale_payments` ADD CONSTRAINT `fk_sale_payments_sale` FOREIGN KEY (`sale_id`) REFERENCES `sales` (`id`) ;
ALTER TABLE `sales` ADD CONSTRAINT `fk_sales_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `sales` ADD CONSTRAINT `fk_sales_customer` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`) ;
ALTER TABLE `saved_report_columns` ADD CONSTRAINT `fk_saved_report_columns_report` FOREIGN KEY (`saved_report_id`) REFERENCES `saved_reports` (`id`) ;
ALTER TABLE `saved_report_parameters` ADD CONSTRAINT `fk_saved_report_parameters_report` FOREIGN KEY (`saved_report_id`) REFERENCES `saved_reports` (`id`) ;
ALTER TABLE `saved_report_shares` ADD CONSTRAINT `fk_saved_report_shares_report` FOREIGN KEY (`saved_report_id`) REFERENCES `saved_reports` (`id`) ;
ALTER TABLE `school_classes` ADD CONSTRAINT `fk_school_classes_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`id`) ;
ALTER TABLE `school_classes` ADD CONSTRAINT `fk_school_classes_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `school_classes` ADD CONSTRAINT `fk_school_classes_grade_level` FOREIGN KEY (`grade_level_id`) REFERENCES `grade_levels` (`id`) ;
ALTER TABLE `school_events` ADD CONSTRAINT `fk_school_events_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `sections` ADD CONSTRAINT `fk_sections_school_class` FOREIGN KEY (`school_class_id`) REFERENCES `school_classes` (`id`) ;
ALTER TABLE `staff_assignments` ADD CONSTRAINT `fk_staff_assignments_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `staff_assignments` ADD CONSTRAINT `fk_staff_assignments_department` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`) ;
ALTER TABLE `staff_assignments` ADD CONSTRAINT `fk_staff_assignments_designation` FOREIGN KEY (`designation_id`) REFERENCES `designations` (`id`) ;
ALTER TABLE `staff_assignments` ADD CONSTRAINT `fk_staff_assignments_staff_profile` FOREIGN KEY (`staff_profile_id`) REFERENCES `staff_profiles` (`id`) ;
ALTER TABLE `staff_attendances` ADD CONSTRAINT `fk_staff_attendances_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `staff_attendances` ADD CONSTRAINT `fk_staff_attendances_person` FOREIGN KEY (`person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `staff_bank_details` ADD CONSTRAINT `fk_staff_bank_details_staff_profile` FOREIGN KEY (`staff_profile_id`) REFERENCES `staff_profiles` (`id`) ;
ALTER TABLE `staff_documents` ADD CONSTRAINT `fk_staff_documents_staff_profile` FOREIGN KEY (`staff_profile_id`) REFERENCES `staff_profiles` (`id`) ;
ALTER TABLE `staff_exits` ADD CONSTRAINT `fk_staff_exits_staff_profile` FOREIGN KEY (`staff_profile_id`) REFERENCES `staff_profiles` (`id`) ;
ALTER TABLE `staff_invites` ADD CONSTRAINT `fk_staff_invites_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ;
ALTER TABLE `staff_profiles` ADD CONSTRAINT `fk_staff_profiles_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `staff_profiles` ADD CONSTRAINT `fk_staff_profiles_department` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`) ;
ALTER TABLE `staff_profiles` ADD CONSTRAINT `fk_staff_profiles_designation` FOREIGN KEY (`designation_id`) REFERENCES `designations` (`id`) ;
ALTER TABLE `staff_profiles` ADD CONSTRAINT `fk_staff_profiles_person` FOREIGN KEY (`person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `staff_profiles` ADD CONSTRAINT `fk_staff_profiles_reporting_manager` FOREIGN KEY (`reporting_manager_id`) REFERENCES `staff_profiles` (`id`) ;
ALTER TABLE `stock_adjustments` ADD CONSTRAINT `fk_stock_adjustments_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `stock_adjustments` ADD CONSTRAINT `fk_stock_adjustments_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`) ;
ALTER TABLE `stock_entries` ADD CONSTRAINT `fk_stock_entries_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `stock_entries` ADD CONSTRAINT `fk_stock_entries_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`) ;
ALTER TABLE `stock_entries` ADD CONSTRAINT `fk_stock_entries_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`) ;
ALTER TABLE `stock_issues` ADD CONSTRAINT `fk_stock_issues_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `stock_issues` ADD CONSTRAINT `fk_stock_issues_department` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`) ;
ALTER TABLE `stock_issues` ADD CONSTRAINT `fk_stock_issues_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`) ;
ALTER TABLE `stock_issues` ADD CONSTRAINT `fk_stock_issues_recipient_person` FOREIGN KEY (`recipient_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `stock_issues` ADD CONSTRAINT `fk_stock_issues_recipient_student` FOREIGN KEY (`recipient_student_id`) REFERENCES `students` (`id`) ;
ALTER TABLE `stock_transfers` ADD CONSTRAINT `fk_stock_transfers_from_campus` FOREIGN KEY (`from_campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `stock_transfers` ADD CONSTRAINT `fk_stock_transfers_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`) ;
ALTER TABLE `stock_transfers` ADD CONSTRAINT `fk_stock_transfers_to_campus` FOREIGN KEY (`to_campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `student_attendances` ADD CONSTRAINT `fk_student_attendances_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`id`) ;
ALTER TABLE `student_attendances` ADD CONSTRAINT `fk_student_attendances_enrollment` FOREIGN KEY (`student_enrollment_id`) REFERENCES `student_enrollments` (`id`) ;
ALTER TABLE `student_attendances` ADD CONSTRAINT `fk_student_attendances_school_class` FOREIGN KEY (`school_class_id`) REFERENCES `school_classes` (`id`) ;
ALTER TABLE `student_attendances` ADD CONSTRAINT `fk_student_attendances_section` FOREIGN KEY (`section_id`) REFERENCES `sections` (`id`) ;
ALTER TABLE `student_documents` ADD CONSTRAINT `fk_student_documents_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`) ;
ALTER TABLE `student_enrollments` ADD CONSTRAINT `fk_student_enrollments_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`id`) ;
ALTER TABLE `student_enrollments` ADD CONSTRAINT `fk_student_enrollments_section` FOREIGN KEY (`section_id`) REFERENCES `sections` (`id`) ;
ALTER TABLE `student_enrollments` ADD CONSTRAINT `fk_student_enrollments_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`) ;
ALTER TABLE `student_exits` ADD CONSTRAINT `fk_student_exits_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`) ;
ALTER TABLE `student_fee_assignments` ADD CONSTRAINT `fk_student_fee_assignments_enrollment` FOREIGN KEY (`student_enrollment_id`) REFERENCES `student_enrollments` (`id`) ;
ALTER TABLE `student_fee_assignments` ADD CONSTRAINT `fk_student_fee_assignments_structure` FOREIGN KEY (`fee_structure_id`) REFERENCES `fee_structures` (`id`) ;
ALTER TABLE `student_guardians` ADD CONSTRAINT `fk_student_guardians_guardian` FOREIGN KEY (`guardian_id`) REFERENCES `guardians` (`id`) ;
ALTER TABLE `student_guardians` ADD CONSTRAINT `fk_student_guardians_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`) ;
ALTER TABLE `student_rewards` ADD CONSTRAINT `fk_student_rewards_granter` FOREIGN KEY (`granted_by_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `student_rewards` ADD CONSTRAINT `fk_student_rewards_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`) ;
ALTER TABLE `student_transfer_requests` ADD CONSTRAINT `fk_student_transfer_requests_from_campus` FOREIGN KEY (`from_campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `student_transfer_requests` ADD CONSTRAINT `fk_student_transfer_requests_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`) ;
ALTER TABLE `student_transfer_requests` ADD CONSTRAINT `fk_student_transfer_requests_to_campus` FOREIGN KEY (`to_campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `student_transport_assignments` ADD CONSTRAINT `fk_transport_assignments_enrollment` FOREIGN KEY (`student_enrollment_id`) REFERENCES `student_enrollments` (`id`) ;
ALTER TABLE `student_transport_assignments` ADD CONSTRAINT `fk_transport_assignments_fee_assignment` FOREIGN KEY (`student_fee_assignment_id`) REFERENCES `student_fee_assignments` (`id`) ;
ALTER TABLE `student_transport_assignments` ADD CONSTRAINT `fk_transport_assignments_route` FOREIGN KEY (`route_id`) REFERENCES `routes` (`id`) ;
ALTER TABLE `student_transport_assignments` ADD CONSTRAINT `fk_transport_assignments_route_stop` FOREIGN KEY (`route_stop_id`) REFERENCES `route_stops` (`id`) ;
ALTER TABLE `student_trip_attendances` ADD CONSTRAINT `fk_trip_attendance_enrollment` FOREIGN KEY (`student_enrollment_id`) REFERENCES `student_enrollments` (`id`) ;
ALTER TABLE `student_trip_attendances` ADD CONSTRAINT `fk_trip_attendance_trip_log` FOREIGN KEY (`trip_log_id`) REFERENCES `trip_logs` (`id`) ;
ALTER TABLE `students` ADD CONSTRAINT `fk_students_person` FOREIGN KEY (`person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `teacher_assignments` ADD CONSTRAINT `fk_teacher_assignments_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`id`) ;
ALTER TABLE `teacher_assignments` ADD CONSTRAINT `fk_teacher_assignments_person` FOREIGN KEY (`teacher_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `teacher_assignments` ADD CONSTRAINT `fk_teacher_assignments_section` FOREIGN KEY (`section_id`) REFERENCES `sections` (`id`) ;
ALTER TABLE `teacher_assignments` ADD CONSTRAINT `fk_teacher_assignments_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`) ;
ALTER TABLE `thread_participants` ADD CONSTRAINT `fk_thread_participants_person` FOREIGN KEY (`person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `thread_participants` ADD CONSTRAINT `fk_thread_participants_thread` FOREIGN KEY (`thread_id`) REFERENCES `message_threads` (`id`) ;
ALTER TABLE `timetable_entries` ADD CONSTRAINT `fk_timetable_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`id`) ;
ALTER TABLE `timetable_entries` ADD CONSTRAINT `fk_timetable_period` FOREIGN KEY (`period_id`) REFERENCES `periods` (`id`) ;
ALTER TABLE `timetable_entries` ADD CONSTRAINT `fk_timetable_section` FOREIGN KEY (`section_id`) REFERENCES `sections` (`id`) ;
ALTER TABLE `timetable_entries` ADD CONSTRAINT `fk_timetable_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`) ;
ALTER TABLE `timetable_entries` ADD CONSTRAINT `fk_timetable_teacher` FOREIGN KEY (`teacher_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `transfer_connector_jobs` ADD CONSTRAINT `fk_transfer_jobs_exit` FOREIGN KEY (`student_exit_id`) REFERENCES `student_exits` (`id`) ;
ALTER TABLE `transfer_connector_jobs` ADD CONSTRAINT `fk_transfer_jobs_schema` FOREIGN KEY (`schema_id`) REFERENCES `regulatory_export_schemas` (`id`) ;
ALTER TABLE `trip_logs` ADD CONSTRAINT `fk_trip_logs_driver` FOREIGN KEY (`driver_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `trip_logs` ADD CONSTRAINT `fk_trip_logs_route` FOREIGN KEY (`route_id`) REFERENCES `routes` (`id`) ;
ALTER TABLE `trip_logs` ADD CONSTRAINT `fk_trip_logs_vehicle` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`) ;
ALTER TABLE `vehicle_documents` ADD CONSTRAINT `fk_vehicle_documents_vehicle` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`) ;
ALTER TABLE `vehicles` ADD CONSTRAINT `fk_vehicles_attendant` FOREIGN KEY (`attendant_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `vehicles` ADD CONSTRAINT `fk_vehicles_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;
ALTER TABLE `vehicles` ADD CONSTRAINT `fk_vehicles_driver` FOREIGN KEY (`driver_person_id`) REFERENCES `persons` (`id`) ;
ALTER TABLE `waivers` ADD CONSTRAINT `fk_waivers_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`) ;
ALTER TABLE `working_calendar_entries` ADD CONSTRAINT `fk_working_calendar_entries_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`id`) ;
ALTER TABLE `working_calendar_entries` ADD CONSTRAINT `fk_working_calendar_entries_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`) ;

-- Reporting (GitHub #185/#187): org-scoped read-only views plus the restricted reporting_ro user.
-- reporting_organisation no longer exposes name/legal_name/slug/status - those live in the
-- platform database now; the school database only holds the organisation's configuration.
CREATE FUNCTION `reporting_current_org_id`() RETURNS bigint
    NO SQL
    DETERMINISTIC
BEGIN
    RETURN @reporting_org_id;
END;

CREATE VIEW `reporting_attendance_daily` AS select `sa`.`id` AS `attendance_id`,`sa`.`organisation_id` AS `organisation_id`,`se`.`student_id` AS `student_id`,trim(concat(`p`.`first_name`,' ',coalesce(`p`.`last_name`,''))) AS `student_name`,`sa`.`academic_year_id` AS `academic_year_id`,coalesce(`sc`.`display_name`,`gl`.`name`) AS `class_name`,`sec`.`name` AS `section_name`,`sa`.`attendance_date` AS `attendance_date`,`sa`.`attendance_status` AS `attendance_status`,`sa`.`is_excused` AS `is_excused` from ((((((`student_attendances` `sa` join `student_enrollments` `se` on((`se`.`id` = `sa`.`student_enrollment_id`))) join `students` `st` on((`st`.`id` = `se`.`student_id`))) join `persons` `p` on((`p`.`id` = `st`.`person_id`))) left join `sections` `sec` on((`sec`.`id` = `sa`.`section_id`))) left join `school_classes` `sc` on((`sc`.`id` = `sa`.`school_class_id`))) left join `grade_levels` `gl` on((`gl`.`id` = `sc`.`grade_level_id`))) where (`sa`.`organisation_id` = `reporting_current_org_id`());

CREATE VIEW `reporting_exam_results` AS select `rc`.`id` AS `report_card_id`,`rc`.`organisation_id` AS `organisation_id`,`e`.`id` AS `exam_id`,`e`.`name` AS `exam_name`,`e`.`exam_type` AS `exam_type`,`e`.`academic_year_id` AS `academic_year_id`,`ay`.`name` AS `academic_year_name`,`se`.`student_id` AS `student_id`,trim(concat(`p`.`first_name`,' ',coalesce(`p`.`last_name`,''))) AS `student_name`,coalesce(`sc`.`display_name`,`gl`.`name`) AS `class_name`,`sec`.`name` AS `section_name`,`rc`.`total_marks_obtained` AS `total_marks_obtained`,`rc`.`total_max_marks` AS `total_max_marks`,`rc`.`percentage` AS `percentage`,`rc`.`overall_grade` AS `overall_grade` from ((((((((`report_cards` `rc` join `exams` `e` on((`e`.`id` = `rc`.`exam_id`))) join `student_enrollments` `se` on((`se`.`id` = `rc`.`student_enrollment_id`))) join `students` `st` on((`st`.`id` = `se`.`student_id`))) join `persons` `p` on((`p`.`id` = `st`.`person_id`))) left join `academic_years` `ay` on((`ay`.`id` = `e`.`academic_year_id`))) left join `sections` `sec` on((`sec`.`id` = `se`.`section_id`))) left join `school_classes` `sc` on((`sc`.`id` = `sec`.`school_class_id`))) left join `grade_levels` `gl` on((`gl`.`id` = `sc`.`grade_level_id`))) where (`rc`.`organisation_id` = `reporting_current_org_id`());

CREATE VIEW `reporting_fee_invoices` AS select `i`.`id` AS `invoice_id`,`i`.`organisation_id` AS `organisation_id`,`i`.`invoice_number` AS `invoice_number`,`se`.`student_id` AS `student_id`,trim(concat(`p`.`first_name`,' ',coalesce(`p`.`last_name`,''))) AS `student_name`,`i`.`academic_year_id` AS `academic_year_id`,`ay`.`name` AS `academic_year_name`,`i`.`total_amount` AS `total_amount`,`i`.`amount_paid` AS `amount_paid`,(`i`.`total_amount` - `i`.`amount_paid`) AS `outstanding_amount`,`i`.`due_date` AS `due_date`,`i`.`status` AS `status` from (((((`invoices` `i` join `student_fee_assignments` `sfa` on((`sfa`.`id` = `i`.`student_fee_assignment_id`))) join `student_enrollments` `se` on((`se`.`id` = `sfa`.`student_enrollment_id`))) join `students` `st` on((`st`.`id` = `se`.`student_id`))) join `persons` `p` on((`p`.`id` = `st`.`person_id`))) left join `academic_years` `ay` on((`ay`.`id` = `i`.`academic_year_id`))) where (`i`.`organisation_id` = `reporting_current_org_id`());

CREATE VIEW `reporting_fee_payments` AS select `pay`.`id` AS `payment_id`,`pay`.`organisation_id` AS `organisation_id`,`pay`.`receipt_number` AS `receipt_number`,`pa`.`invoice_id` AS `invoice_id`,`inv`.`invoice_number` AS `invoice_number`,`se`.`student_id` AS `student_id`,trim(concat(`p`.`first_name`,' ',coalesce(`p`.`last_name`,''))) AS `student_name`,`pay`.`academic_year_id` AS `academic_year_id`,`pa`.`allocated_amount` AS `allocated_amount`,`pay`.`amount` AS `payment_amount`,`pay`.`payment_method` AS `payment_method`,`pay`.`payment_date` AS `payment_date`,`pay`.`status` AS `status` from ((((((`payments` `pay` join `payment_allocations` `pa` on((`pa`.`payment_id` = `pay`.`id`))) join `invoices` `inv` on((`inv`.`id` = `pa`.`invoice_id`))) join `student_fee_assignments` `sfa` on((`sfa`.`id` = `inv`.`student_fee_assignment_id`))) join `student_enrollments` `se` on((`se`.`id` = `sfa`.`student_enrollment_id`))) join `students` `st` on((`st`.`id` = `se`.`student_id`))) join `persons` `p` on((`p`.`id` = `st`.`person_id`))) where (`pay`.`organisation_id` = `reporting_current_org_id`());

CREATE VIEW `reporting_inventory_stock` AS select `it`.`id` AS `item_id`,`it`.`organisation_id` AS `organisation_id`,`it`.`code` AS `item_code`,`it`.`name` AS `item_name`,`it`.`reorder_level` AS `reorder_level`,`camp`.`id` AS `campus_id`,`camp`.`name` AS `campus_name`,coalesce(`e`.`total_received`,0) AS `quantity_received`,coalesce(`iss`.`total_issued`,0) AS `quantity_issued`,coalesce(`adj`.`total_adjusted`,0) AS `quantity_adjusted`,((coalesce(`e`.`total_received`,0) - coalesce(`iss`.`total_issued`,0)) + coalesce(`adj`.`total_adjusted`,0)) AS `balance`,coalesce(`e`.`total_received_cost`,0) AS `received_cost` from ((((((select `stock_entries`.`item_id` AS `item_id`,`stock_entries`.`campus_id` AS `campus_id` from `stock_entries` union select `stock_issues`.`item_id` AS `item_id`,`stock_issues`.`campus_id` AS `campus_id` from `stock_issues` union select `stock_adjustments`.`item_id` AS `item_id`,`stock_adjustments`.`campus_id` AS `campus_id` from `stock_adjustments`) `active` join `items` `it` on((`it`.`id` = `active`.`item_id`))) join `campuses` `camp` on((`camp`.`id` = `active`.`campus_id`))) left join (select `stock_entries`.`item_id` AS `item_id`,`stock_entries`.`campus_id` AS `campus_id`,sum(`stock_entries`.`quantity`) AS `total_received`,sum((`stock_entries`.`quantity` * coalesce(`stock_entries`.`unit_cost`,0))) AS `total_received_cost` from `stock_entries` group by `stock_entries`.`item_id`,`stock_entries`.`campus_id`) `e` on(((`e`.`item_id` = `active`.`item_id`) and (`e`.`campus_id` = `active`.`campus_id`)))) left join (select `stock_issues`.`item_id` AS `item_id`,`stock_issues`.`campus_id` AS `campus_id`,sum(`stock_issues`.`quantity`) AS `total_issued` from `stock_issues` group by `stock_issues`.`item_id`,`stock_issues`.`campus_id`) `iss` on(((`iss`.`item_id` = `active`.`item_id`) and (`iss`.`campus_id` = `active`.`campus_id`)))) left join (select `stock_adjustments`.`item_id` AS `item_id`,`stock_adjustments`.`campus_id` AS `campus_id`,sum(`stock_adjustments`.`quantity_delta`) AS `total_adjusted` from `stock_adjustments` group by `stock_adjustments`.`item_id`,`stock_adjustments`.`campus_id`) `adj` on(((`adj`.`item_id` = `active`.`item_id`) and (`adj`.`campus_id` = `active`.`campus_id`)))) where (`it`.`organisation_id` = `reporting_current_org_id`());

CREATE VIEW `reporting_organisation` AS select `oc`.`organisation_id` AS `organisation_id`,`oc`.`timezone` AS `timezone`,`oc`.`default_currency` AS `default_currency`,`oc`.`date_format` AS `date_format`,`oc`.`working_days_mask` AS `working_days_mask` from `organisation_configurations` `oc` where (`oc`.`organisation_id` = `reporting_current_org_id`());

CREATE VIEW `reporting_purchase_orders` AS select `po`.`id` AS `purchase_order_id`,`po`.`organisation_id` AS `organisation_id`,`sup`.`name` AS `supplier_name`,`camp`.`name` AS `campus_name`,`po`.`expected_date` AS `expected_date`,`po`.`status` AS `status`,`pol`.`item_id` AS `item_id`,`it`.`name` AS `item_name`,`pol`.`quantity` AS `quantity_ordered`,`pol`.`quantity_received` AS `quantity_received`,`pol`.`unit_cost` AS `unit_cost`,(`pol`.`quantity` * `pol`.`unit_cost`) AS `line_amount` from ((((`purchase_orders` `po` join `purchase_order_lines` `pol` on((`pol`.`purchase_order_id` = `po`.`id`))) join `suppliers` `sup` on((`sup`.`id` = `po`.`supplier_id`))) join `campuses` `camp` on((`camp`.`id` = `po`.`campus_id`))) join `items` `it` on((`it`.`id` = `pol`.`item_id`))) where (`po`.`organisation_id` = `reporting_current_org_id`());

CREATE VIEW `reporting_sales` AS select `s`.`id` AS `sale_id`,`s`.`organisation_id` AS `organisation_id`,`s`.`receipt_number` AS `receipt_number`,`s`.`sale_date` AS `sale_date`,`camp`.`name` AS `campus_name`,`cust`.`name` AS `customer_name`,`sl`.`item_id` AS `item_id`,`it`.`name` AS `item_name`,`sl`.`quantity` AS `quantity`,`sl`.`unit_price` AS `unit_price`,(`sl`.`quantity` * `sl`.`unit_price`) AS `line_total`,`s`.`total_amount` AS `sale_total_amount`,`s`.`amount_paid` AS `sale_amount_paid`,`s`.`status` AS `status` from ((((`sales` `s` join `sale_lines` `sl` on((`sl`.`sale_id` = `s`.`id`))) join `items` `it` on((`it`.`id` = `sl`.`item_id`))) join `customers` `cust` on((`cust`.`id` = `s`.`customer_id`))) join `campuses` `camp` on((`camp`.`id` = `s`.`campus_id`))) where (`s`.`organisation_id` = `reporting_current_org_id`());

CREATE VIEW `reporting_staff` AS select `sp`.`id` AS `staff_id`,`sp`.`organisation_id` AS `organisation_id`,`sp`.`employee_code` AS `employee_code`,trim(concat(`p`.`first_name`,' ',coalesce(`p`.`last_name`,''))) AS `staff_name`,`d`.`name` AS `designation_name`,`dept`.`name` AS `department_name`,`sp`.`employment_type` AS `employment_type`,`sp`.`date_of_joining` AS `date_of_joining`,`sp`.`status` AS `employment_status` from (((`staff_profiles` `sp` join `persons` `p` on((`p`.`id` = `sp`.`person_id`))) join `designations` `d` on((`d`.`id` = `sp`.`designation_id`))) left join `departments` `dept` on((`dept`.`id` = `sp`.`department_id`))) where (`sp`.`organisation_id` = `reporting_current_org_id`());

CREATE VIEW `reporting_students` AS select `s`.`id` AS `student_id`,`s`.`organisation_id` AS `organisation_id`,`s`.`admission_number` AS `admission_number`,`s`.`admission_date` AS `admission_date`,trim(concat(`p`.`first_name`,' ',coalesce(`p`.`last_name`,''))) AS `student_name`,`p`.`gender` AS `gender`,`p`.`date_of_birth` AS `date_of_birth`,`se`.`academic_year_id` AS `academic_year_id`,`ay`.`name` AS `academic_year_name`,coalesce(`sc`.`display_name`,`gl`.`name`) AS `class_name`,`sec`.`name` AS `section_name`,`se`.`roll_number` AS `roll_number`,`s`.`status` AS `student_status` from ((((((`students` `s` join `persons` `p` on((`p`.`id` = `s`.`person_id`))) left join `student_enrollments` `se` on(((`se`.`student_id` = `s`.`id`) and (`se`.`is_current` = true)))) left join `academic_years` `ay` on((`ay`.`id` = `se`.`academic_year_id`))) left join `sections` `sec` on((`sec`.`id` = `se`.`section_id`))) left join `school_classes` `sc` on((`sc`.`id` = `sec`.`school_class_id`))) left join `grade_levels` `gl` on((`gl`.`id` = `sc`.`grade_level_id`))) where (`s`.`organisation_id` = `reporting_current_org_id`());

-- Dev-only seeded password, rotate before any real deployment (see ReportingDataSourceConfig).
CREATE USER IF NOT EXISTS 'reporting_ro'@'%' IDENTIFIED BY 'changeme-reporting-ro';
GRANT SELECT ON `reporting_attendance_daily` TO 'reporting_ro'@'%';
GRANT SELECT ON `reporting_exam_results` TO 'reporting_ro'@'%';
GRANT SELECT ON `reporting_fee_invoices` TO 'reporting_ro'@'%';
GRANT SELECT ON `reporting_fee_payments` TO 'reporting_ro'@'%';
GRANT SELECT ON `reporting_inventory_stock` TO 'reporting_ro'@'%';
GRANT SELECT ON `reporting_organisation` TO 'reporting_ro'@'%';
GRANT SELECT ON `reporting_purchase_orders` TO 'reporting_ro'@'%';
GRANT SELECT ON `reporting_sales` TO 'reporting_ro'@'%';
GRANT SELECT ON `reporting_staff` TO 'reporting_ro'@'%';
GRANT SELECT ON `reporting_students` TO 'reporting_ro'@'%';
FLUSH PRIVILEGES;

-- Reference data
INSERT INTO `permissions` (`id`, `code`, `module`, `description`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES (1,'STUDENT_VIEW','student','View student records','2026-10-07 16:06:19.499047','2026-10-07 16:06:19.499047',NULL,NULL),(5,'ATTENDANCE_VIEW','attendance','View attendance records','2026-10-07 16:06:19.499047','2026-10-07 16:06:19.499047',NULL,NULL),(6,'ATTENDANCE_MARK','attendance','Mark or correct attendance','2026-10-07 16:06:19.499047','2026-10-07 16:06:19.499047',NULL,NULL),(7,'FEE_VIEW','fees','View fee/invoice records','2026-10-07 16:06:19.499047','2026-10-07 16:06:19.499047',NULL,NULL),(8,'FEE_COLLECT','fees','Record fee payments','2026-10-07 16:06:19.499047','2026-10-07 16:06:19.499047',NULL,NULL),(9,'FEE_DISCOUNT_APPROVE','fees','Approve fee discounts/scholarships','2026-10-07 16:06:19.499047','2026-10-07 16:06:19.499047',NULL,NULL),(10,'REPORT_VIEW','reporting','View operational/academic reports','2026-10-07 16:06:19.499047','2026-10-07 16:06:19.499047',NULL,NULL),(11,'ROLE_MANAGE','authorization','Create/edit roles and their permissions','2026-10-07 16:06:19.499047','2026-10-07 16:06:19.499047',NULL,NULL),(12,'MEMBERSHIP_MANAGE','authorization','Grant/revoke organisation memberships','2026-10-07 16:06:19.499047','2026-10-07 16:06:19.499047',NULL,NULL),(13,'ORGANISATION_MANAGE','organisation','Manage organisation profile and configuration','2026-10-07 16:06:19.499047','2026-10-07 16:06:19.499047',NULL,NULL),(14,'GRADE_LEVEL_VIEW','academic','View grade level catalog','2026-10-07 16:06:19.609524','2026-10-07 16:06:19.609524',NULL,NULL),(15,'GRADE_LEVEL_MANAGE','academic','Create/edit grade levels','2026-10-07 16:06:19.609524','2026-10-07 16:06:19.609524',NULL,NULL),(16,'SUBJECT_VIEW','academic','View subject catalog','2026-10-07 16:06:19.609524','2026-10-07 16:06:19.609524',NULL,NULL),(17,'SUBJECT_MANAGE','academic','Create/edit subjects','2026-10-07 16:06:19.609524','2026-10-07 16:06:19.609524',NULL,NULL),(18,'CLASS_VIEW','academic','View classes and sections','2026-10-07 16:06:19.609524','2026-10-07 16:06:19.609524',NULL,NULL),(19,'CLASS_MANAGE','academic','Create/edit classes and sections','2026-10-07 16:06:19.609524','2026-10-07 16:06:19.609524',NULL,NULL),(20,'TEACHER_ASSIGNMENT_VIEW','academic','View teacher assignments','2026-10-07 16:06:19.609524','2026-10-07 16:06:19.609524',NULL,NULL),(21,'TEACHER_ASSIGNMENT_MANAGE','academic','Assign/reassign teachers to sections','2026-10-07 16:06:19.609524','2026-10-07 16:06:19.609524',NULL,NULL),(22,'STUDENT_MANAGE','student','Admit/edit student records','2026-10-07 16:06:19.694191','2026-10-07 16:06:19.694191',NULL,NULL),(23,'STUDENT_ENROLLMENT_MANAGE','student','Enroll, promote, and reassign students','2026-10-07 16:06:19.694191','2026-10-07 16:06:19.694191',NULL,NULL),(24,'STUDENT_DOCUMENT_VIEW','student','View student documents','2026-10-07 16:06:19.694191','2026-10-07 16:06:19.694191',NULL,NULL),(25,'STUDENT_DOCUMENT_MANAGE','student','Upload and verify student documents','2026-10-07 16:06:19.694191','2026-10-07 16:06:19.694191',NULL,NULL),(26,'STUDENT_EXIT_MANAGE','student','Record student transfers/withdrawals/exits','2026-10-07 16:06:19.694191','2026-10-07 16:06:19.694191',NULL,NULL),(27,'GUARDIAN_VIEW','parent','View guardians and student-guardian links','2026-10-07 16:06:19.694191','2026-10-07 16:06:19.694191',NULL,NULL),(28,'GUARDIAN_MANAGE','parent','Create guardians and manage student links','2026-10-07 16:06:19.694191','2026-10-07 16:06:19.694191',NULL,NULL),(30,'ATTENDANCE_CORRECT','attendance','Correct an already-marked attendance record','2026-10-07 16:06:19.759312','2026-10-07 16:06:19.759312',NULL,NULL),(31,'ATTENDANCE_LOCK','attendance','Submit and lock a class attendance register','2026-10-07 16:06:19.759312','2026-10-07 16:06:19.759312',NULL,NULL),(32,'STAFF_ATTENDANCE_MARK','attendance','Record staff check-in/check-out','2026-10-07 16:06:19.759312','2026-10-07 16:06:19.759312',NULL,NULL),(33,'STAFF_ATTENDANCE_VIEW','attendance','View staff attendance records','2026-10-07 16:06:19.759312','2026-10-07 16:06:19.759312',NULL,NULL),(36,'FEE_CATEGORY_MANAGE','finance','Create and manage fee categories','2026-10-07 16:06:19.848394','2026-10-07 16:06:19.848394',NULL,NULL),(37,'FEE_STRUCTURE_MANAGE','finance','Create and manage fee structures/installments','2026-10-07 16:06:19.848394','2026-10-07 16:06:19.848394',NULL,NULL),(38,'FEE_ASSIGNMENT_MANAGE','finance','Assign fee structures to student enrollments','2026-10-07 16:06:19.848394','2026-10-07 16:06:19.848394',NULL,NULL),(39,'FEE_INVOICE_MANAGE','finance','Generate invoices','2026-10-07 16:06:19.848394','2026-10-07 16:06:19.848394',NULL,NULL),(40,'FEE_REFUND_APPROVE','finance','Approve and record refunds','2026-10-07 16:06:19.848394','2026-10-07 16:06:19.848394',NULL,NULL),(44,'EXAM_MANAGE','examination','Create and manage exams and exam schedules','2026-10-07 16:06:19.895044','2026-10-07 16:06:19.895044',NULL,NULL),(45,'MARKS_ENTER','examination','Enter marks for an exam schedule','2026-10-07 16:06:19.895044','2026-10-07 16:06:19.895044',NULL,NULL),(46,'MARKS_CORRECT','examination','Correct an already-entered marks record','2026-10-07 16:06:19.895044','2026-10-07 16:06:19.895044',NULL,NULL),(47,'GRADING_SCALE_MANAGE','examination','Create and manage grading scales','2026-10-07 16:06:19.895044','2026-10-07 16:06:19.895044',NULL,NULL),(48,'REPORT_CARD_PUBLISH','examination','Publish a student report card','2026-10-07 16:06:19.895044','2026-10-07 16:06:19.895044',NULL,NULL),(49,'EXAM_VIEW','examination','View exams, schedules, marks, report cards','2026-10-07 16:06:19.895044','2026-10-07 16:06:19.895044',NULL,NULL),(50,'ANNOUNCEMENT_CREATE','communication','Create a draft announcement','2026-10-07 16:06:19.934076','2026-10-07 16:06:19.934076',NULL,NULL),(51,'ANNOUNCEMENT_PUBLISH','communication','Publish an announcement and fan out to its audience','2026-10-07 16:06:19.934076','2026-10-07 16:06:19.934076',NULL,NULL),(52,'ANNOUNCEMENT_CANCEL','communication','Cancel a draft announcement','2026-10-07 16:06:19.934076','2026-10-07 16:06:19.934076',NULL,NULL),(53,'NOTIFICATION_TEMPLATE_MANAGE','communication','Create and manage notification templates','2026-10-07 16:06:19.934076','2026-10-07 16:06:19.934076',NULL,NULL),(54,'COMMUNICATION_VIEW','communication','View announcements and notifications','2026-10-07 16:06:19.934076','2026-10-07 16:06:19.934076',NULL,NULL),(55,'TRANSPORT_VEHICLE_MANAGE','transport','Create and manage vehicles','2026-10-07 16:06:19.997034','2026-10-07 16:06:19.997034',NULL,NULL),(56,'TRANSPORT_ROUTE_MANAGE','transport','Create and manage routes and stops','2026-10-07 16:06:19.997034','2026-10-07 16:06:19.997034',NULL,NULL),(57,'TRANSPORT_ASSIGNMENT_MANAGE','transport','Assign, reassign, and end student transport assignments','2026-10-07 16:06:19.997034','2026-10-07 16:06:19.997034',NULL,NULL),(58,'TRANSPORT_TRIP_LOG','transport','Start, complete, and cancel trip logs','2026-10-07 16:06:19.997034','2026-10-07 16:06:19.997034',NULL,NULL),(59,'TRANSPORT_VIEW','transport','View vehicles, routes, assignments, and trip logs','2026-10-07 16:06:19.997034','2026-10-07 16:06:19.997034',NULL,NULL),(60,'LIBRARY_CATALOG_MANAGE','library','Create and manage books and book copies','2026-10-07 16:06:20.054415','2026-10-07 16:06:20.054415',NULL,NULL),(61,'LIBRARY_BORROW_MANAGE','library','Issue, return, and mark book copies lost','2026-10-07 16:06:20.054415','2026-10-07 16:06:20.054415',NULL,NULL),(62,'LIBRARY_FINE_MANAGE','library','Raise, pay, and waive fines','2026-10-07 16:06:20.054415','2026-10-07 16:06:20.054415',NULL,NULL),(63,'LIBRARY_VIEW','library','View books, copies, borrow records, and fines','2026-10-07 16:06:20.054415','2026-10-07 16:06:20.054415',NULL,NULL),(64,'INVENTORY_CATALOG_MANAGE','inventory','Create and manage item categories and items','2026-10-07 16:06:20.099269','2026-10-07 16:06:20.099269',NULL,NULL),(65,'INVENTORY_STOCK_MANAGE','inventory','Record stock entries and issues','2026-10-07 16:06:20.099269','2026-10-07 16:06:20.099269',NULL,NULL),(66,'INVENTORY_ADJUSTMENT_MANAGE','inventory','Raise stock adjustments (damage/loss/count correction)','2026-10-07 16:06:20.099269','2026-10-07 16:06:20.099269',NULL,NULL),(67,'INVENTORY_VIEW','inventory','View items, stock ledger entries, and balances','2026-10-07 16:06:20.099269','2026-10-07 16:06:20.099269',NULL,NULL),(68,'HR_STAFF_MANAGE','hr','Create and manage staff profiles and documents','2026-10-07 16:06:20.145913','2026-10-07 16:06:20.145913',NULL,NULL),(69,'HR_LEAVE_TYPE_MANAGE','hr','Create leave types and allocate leave balances','2026-10-07 16:06:20.145913','2026-10-07 16:06:20.145913',NULL,NULL),(70,'HR_LEAVE_MANAGE','hr','Raise, approve, reject, and cancel leave requests','2026-10-07 16:06:20.145913','2026-10-07 16:06:20.145913',NULL,NULL),(71,'HR_VIEW','hr','View staff profiles, leave balances, and requests','2026-10-07 16:06:20.145913','2026-10-07 16:06:20.145913',NULL,NULL),(72,'PARENT_PORTAL_ACCESS','parent','Log into the parent portal and view own children\'s data','2026-10-07 16:06:20.214304','2026-10-07 16:06:20.214304',NULL,NULL),(73,'MEMBERSHIP_VIEW','authorization','View organisation membership list','2026-10-07 16:06:20.385836','2026-10-07 16:06:20.385836',NULL,NULL),(74,'INVENTORY_SUPPLIER_MANAGE','inventory','Create and manage suppliers','2026-10-07 16:06:20.439605','2026-10-07 16:06:20.439605',NULL,NULL),(75,'INVENTORY_CUSTOMER_MANAGE','inventory','Create and manage store-sales customers','2026-10-07 16:06:20.458809','2026-10-07 16:06:20.458809',NULL,NULL),(76,'PURCHASE_VIEW','purchase','View purchase orders, lines, and returns','2026-10-07 16:06:20.484252','2026-10-07 16:06:20.484252',NULL,NULL),(77,'PURCHASE_MANAGE','purchase','Create, submit, approve, cancel, and receive purchase orders; record purchase returns','2026-10-07 16:06:20.484252','2026-10-07 16:06:20.484252',NULL,NULL),(78,'SALES_VIEW','sales','View sales, receipts, and customer purchase history','2026-10-07 16:06:20.538008','2026-10-07 16:06:20.538008',NULL,NULL),(79,'SALES_MANAGE','sales','Create sales and record payments against them','2026-10-07 16:06:20.538008','2026-10-07 16:06:20.538008',NULL,NULL),(80,'REPORT_CREATE','reporting','Create and own saved reports','2026-10-07 16:06:20.578690','2026-10-07 16:06:20.578690',NULL,NULL),(81,'REPORT_MANAGE','reporting','View, edit, and archive any saved report in the organisation','2026-10-07 16:06:20.578690','2026-10-07 16:06:20.578690',NULL,NULL),(82,'REPORT_EXPORT','reporting','Export report results','2026-10-07 16:06:20.578690','2026-10-07 16:06:20.578690',NULL,NULL),(83,'ALL_FUNCTIONS','authorization','Bypasses every permission-gated action - an admin-equivalent grant usable by any role','2026-10-07 16:06:20.644060','2026-10-07 16:06:20.644060',NULL,NULL),(84,'STUDENT_TRANSFER_MANAGE','student','Raise, approve, and reject intra-org campus transfer requests','2026-10-07 16:06:21.015667','2026-10-07 16:06:21.015667',NULL,NULL),(85,'HR_RECRUITMENT_MANAGE','hr','Review, approve, and reject job applications','2026-10-07 16:06:21.033087','2026-10-07 16:06:21.033087',NULL,NULL),(86,'PROFILE_CHANGE_MANAGE','identity','Review, approve, and reject self-service profile change requests','2026-10-07 16:06:21.073032','2026-10-07 16:06:21.073032',NULL,NULL),(87,'HR_PAYROLL_VIEW','hr','View and manage staff bank and tax details','2026-10-07 16:06:21.144587','2026-10-07 16:06:21.144587',NULL,NULL),(88,'ATTENDANCE_UNLOCK','attendance','Unlock a LOCKED class attendance register so it can be corrected again','2026-10-07 16:06:21.190353','2026-10-07 16:06:21.190353',NULL,NULL),(89,'MARKS_SUBMIT','examination','Submit an entered marks register for review','2026-10-07 16:06:21.219156','2026-10-07 16:06:21.219156',NULL,NULL),(90,'MARKS_APPROVE','examination','Approve a submitted marks register for publish','2026-10-07 16:06:21.219156','2026-10-07 16:06:21.219156',NULL,NULL),(91,'MARKS_CORRECT_AFTER_PUBLISH','examination','Correct marks after a report card has already been published, flagging it stale','2026-10-07 16:06:21.252259','2026-10-07 16:06:21.252259',NULL,NULL),(92,'FEE_ADJUSTMENT_MANAGE','finance','Record a manual correction to what is owed on an invoice','2026-10-07 16:06:21.332067','2026-10-07 16:06:21.332067',NULL,NULL),(93,'FEE_WAIVER_APPROVE','finance','Approve and record a waiver against an invoice','2026-10-07 16:06:21.332067','2026-10-07 16:06:21.332067',NULL,NULL),(94,'FEE_REMINDER_SEND','finance','Send an overdue-fee reminder to a student\'s guardians','2026-10-07 16:06:21.332067','2026-10-07 16:06:21.332067',NULL,NULL),(95,'ACADEMIC_CONFIGURATION_VIEW','academic','View school timings (start/end time)','2026-10-07 16:06:21.353272','2026-10-07 16:06:21.353272',NULL,NULL),(96,'ACADEMIC_CONFIGURATION_MANAGE','academic','Edit school timings (start/end time)','2026-10-07 16:06:21.353272','2026-10-07 16:06:21.353272',NULL,NULL),(97,'WORKING_CALENDAR_VIEW','academic','View holidays and special working days','2026-10-07 16:06:21.353272','2026-10-07 16:06:21.353272',NULL,NULL),(98,'WORKING_CALENDAR_MANAGE','academic','Add/remove holidays and special working days','2026-10-07 16:06:21.353272','2026-10-07 16:06:21.353272',NULL,NULL),(99,'SCHOOL_EVENT_VIEW','academic','View the school events calendar','2026-10-07 16:06:21.668592','2026-10-07 16:06:21.668592',NULL,NULL),(100,'SCHOOL_EVENT_MANAGE','academic','Add/remove ad-hoc school events','2026-10-07 16:06:21.668592','2026-10-07 16:06:21.668592',NULL,NULL),(101,'INVENTORY_ASSET_MANAGE','inventory','Create and manage high-value asset records','2026-10-07 16:06:21.743528','2026-10-07 16:06:21.743528',NULL,NULL),(102,'TIMETABLE_VIEW','academic','View period structure and class/teacher timetables','2026-10-07 16:06:21.769268','2026-10-07 16:06:21.769268',NULL,NULL),(103,'TIMETABLE_MANAGE','academic','Configure periods and build class timetables','2026-10-07 16:06:21.769268','2026-10-07 16:06:21.769268',NULL,NULL),(104,'PAYROLL_VIEW','hr','View salary components, salary structures, payroll runs and payslips','2026-10-07 16:06:21.886206','2026-10-07 16:06:21.886206',NULL,NULL),(105,'PAYROLL_MANAGE','hr','Configure salary components and structures, generate and finalize payroll runs','2026-10-07 16:06:21.886206','2026-10-07 16:06:21.886206',NULL,NULL),(106,'LEARNING_VIEW','learning','View homework, quizzes, materials, lesson plans, answer sheets and leaderboards across all sections','2026-10-07 16:06:21.953477','2026-10-07 16:06:21.953477',NULL,NULL),(107,'LEARNING_MANAGE','learning','Create and manage learning content and grant rewards for any section','2026-10-07 16:06:21.953477','2026-10-07 16:06:21.953477',NULL,NULL),(108,'LESSON_PLAN_REVIEW','learning','Approve or reject submitted lesson plans','2026-10-07 16:06:21.953477','2026-10-07 16:06:21.953477',NULL,NULL),(109,'REGULATORY_EXPORT_MANAGE','organisation','Define regulatory export schemas, run exports and manage transfer connector jobs','2026-10-07 16:06:22.142979','2026-10-07 16:06:22.142979',NULL,NULL),(110,'PRIVACY_MANAGE','organisation','Manage consent purposes and records, and handle data subject requests','2026-10-07 16:06:22.178690','2026-10-07 16:06:22.178690',NULL,NULL);
INSERT INTO `school_starter_needs` (`id`, `need_key`, `label`, `help_text`, `default_yes`, `baseline`, `display_order`, `module_codes`, `created_at`, `updated_at`) VALUES (1,'BASELINE','Core school operations','Included for every school.',1,1,0,'DASHBOARD,STUDENTS,ACADEMICS,ATTENDANCE,REPORTS','2026-10-07 16:06:22.311899','2026-10-07 16:06:22.311899'),(2,'FEES','Do you collect fees through Operion?','Fee structures, invoices, receipts and dues.',1,0,10,'FEES','2026-10-07 16:06:22.311899','2026-10-07 16:06:22.311899'),(3,'EXAMS','Do you run exams and issue report cards?','Exam schedules, marks entry, grading and report cards.',1,0,20,'EXAMINATIONS','2026-10-07 16:06:22.311899','2026-10-07 16:06:22.311899'),(4,'LEARNING','Do teachers share homework, assignments or study material?',NULL,1,0,30,'LEARNING','2026-10-07 16:06:22.311899','2026-10-07 16:06:22.311899'),(5,'COMMUNICATION','Do you send announcements and messages to parents and staff?',NULL,1,0,40,'COMMUNICATION,MESSAGING','2026-10-07 16:06:22.311899','2026-10-07 16:06:22.311899'),(6,'TRANSPORT','Do you run school buses or arrange student transport?','Vehicles, routes and student transport assignments. Needs Fees.',0,0,50,'TRANSPORT','2026-10-07 16:06:22.311899','2026-10-07 16:06:22.311899'),(7,'LIBRARY','Do you have a library with book borrowing?',NULL,0,0,60,'LIBRARY','2026-10-07 16:06:22.311899','2026-10-07 16:06:22.311899'),(8,'HR','Do you manage staff records and leave here?',NULL,1,0,70,'HR','2026-10-07 16:06:22.311899','2026-10-07 16:06:22.311899'),(9,'PAYROLL','Do you run staff payroll here?','Needs HR.',0,0,80,'PAYROLL','2026-10-07 16:06:22.311899','2026-10-07 16:06:22.311899'),(10,'INVENTORY','Do you track stores and stock?',NULL,0,0,90,'INVENTORY','2026-10-07 16:06:22.311899','2026-10-07 16:06:22.311899'),(11,'PROCUREMENT','Do you buy from suppliers or sell items such as uniforms and books?','Purchase orders and sales. Needs Inventory.',0,0,100,'PURCHASE,SALES','2026-10-07 16:06:22.311899','2026-10-07 16:06:22.311899'),(12,'COMPLIANCE','Do you need regulatory exports, consent records or data-subject requests?',NULL,0,0,110,'COMPLIANCE','2026-10-07 16:06:22.311899','2026-10-07 16:06:22.311899');
INSERT INTO `school_template_master_items` (`id`, `category`, `name`, `code`, `description`, `sequence_order`, `stage`, `category_type`, `min_percentage`, `remark`, `permission_codes`, `created_at`, `updated_at`, `created_by`, `updated_by`, `module_code`) VALUES (1,'GRADE','LKG',NULL,NULL,0,'Pre-Primary',NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519130','2026-10-07 16:06:21.519130',NULL,NULL,NULL),(2,'GRADE','UKG',NULL,NULL,1,'Pre-Primary',NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519130','2026-10-07 16:06:21.519130',NULL,NULL,NULL),(3,'GRADE','Grade 1',NULL,NULL,2,'Primary',NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519130','2026-10-07 16:06:21.519130',NULL,NULL,NULL),(4,'GRADE','Grade 2',NULL,NULL,3,'Primary',NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519130','2026-10-07 16:06:21.519130',NULL,NULL,NULL),(5,'GRADE','Grade 3',NULL,NULL,4,'Primary',NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519130','2026-10-07 16:06:21.519130',NULL,NULL,NULL),(6,'GRADE','Grade 4',NULL,NULL,5,'Primary',NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519130','2026-10-07 16:06:21.519130',NULL,NULL,NULL),(7,'GRADE','Grade 5',NULL,NULL,6,'Primary',NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519130','2026-10-07 16:06:21.519130',NULL,NULL,NULL),(8,'GRADE','Grade 6',NULL,NULL,7,'Secondary',NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519130','2026-10-07 16:06:21.519130',NULL,NULL,NULL),(9,'GRADE','Grade 7',NULL,NULL,8,'Secondary',NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519130','2026-10-07 16:06:21.519130',NULL,NULL,NULL),(10,'GRADE','Grade 8',NULL,NULL,9,'Secondary',NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519130','2026-10-07 16:06:21.519130',NULL,NULL,NULL),(11,'GRADE','Grade 9',NULL,NULL,10,'Secondary',NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519130','2026-10-07 16:06:21.519130',NULL,NULL,NULL),(12,'GRADE','Grade 10',NULL,NULL,11,'Secondary',NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519130','2026-10-07 16:06:21.519130',NULL,NULL,NULL),(13,'DEPARTMENT','Administration',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519517','2026-10-07 16:06:21.519517',NULL,NULL,NULL),(14,'DEPARTMENT','Academics',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519517','2026-10-07 16:06:21.519517',NULL,NULL,NULL),(15,'DEPARTMENT','Finance & Accounts',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519517','2026-10-07 16:06:21.519517',NULL,NULL,NULL),(16,'DEPARTMENT','HR',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519517','2026-10-07 16:06:21.519517',NULL,NULL,'HR'),(17,'DEPARTMENT','Operations',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519517','2026-10-07 16:06:21.519517',NULL,NULL,NULL),(18,'DEPARTMENT','Transport',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519517','2026-10-07 16:06:21.519517',NULL,NULL,'TRANSPORT'),(19,'DEPARTMENT','Library',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519517','2026-10-07 16:06:21.519517',NULL,NULL,'LIBRARY'),(20,'DEPARTMENT','IT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519517','2026-10-07 16:06:21.519517',NULL,NULL,NULL),(21,'DEPARTMENT','Examination',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519517','2026-10-07 16:06:21.519517',NULL,NULL,'EXAMINATIONS'),(22,'DEPARTMENT','Admissions',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519517','2026-10-07 16:06:21.519517',NULL,NULL,NULL),(23,'DESIGNATION','Principal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,NULL),(24,'DESIGNATION','Vice Principal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,NULL),(25,'DESIGNATION','HOD',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,NULL),(26,'DESIGNATION','Academic Coordinator',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,NULL),(27,'DESIGNATION','Teacher',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,NULL),(28,'DESIGNATION','Assistant Teacher',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,NULL),(29,'DESIGNATION','Accountant',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,'FEES'),(30,'DESIGNATION','HR Manager',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,'HR'),(31,'DESIGNATION','Admin Officer',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,NULL),(32,'DESIGNATION','Receptionist',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,NULL),(33,'DESIGNATION','Librarian',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,'LIBRARY'),(34,'DESIGNATION','Transport Manager',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,'TRANSPORT'),(35,'DESIGNATION','Driver',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,'TRANSPORT'),(36,'DESIGNATION','Storekeeper',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,'INVENTORY'),(37,'DESIGNATION','Purchase Manager',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,'PURCHASE'),(38,'DESIGNATION','IT Admin',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,NULL),(39,'DESIGNATION','Support Staff',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.519744','2026-10-07 16:06:21.519744',NULL,NULL,NULL),(40,'ROLE','Administrator',NULL,'Broad day-to-day operational access, short of financial approvals and payroll',NULL,NULL,NULL,NULL,NULL,'ORGANISATION_MANAGE,ROLE_MANAGE,MEMBERSHIP_MANAGE,MEMBERSHIP_VIEW,PROFILE_CHANGE_MANAGE,ACADEMIC_CONFIGURATION_MANAGE,ACADEMIC_CONFIGURATION_VIEW,CLASS_MANAGE,CLASS_VIEW,GRADE_LEVEL_MANAGE,GRADE_LEVEL_VIEW,SUBJECT_MANAGE,SUBJECT_VIEW,TEACHER_ASSIGNMENT_MANAGE,TEACHER_ASSIGNMENT_VIEW,WORKING_CALENDAR_MANAGE,WORKING_CALENDAR_VIEW,STUDENT_MANAGE,STUDENT_CREATE,STUDENT_UPDATE,STUDENT_VIEW,STUDENT_ENROLLMENT_MANAGE,STUDENT_TRANSFER_MANAGE,STUDENT_EXIT_MANAGE,STUDENT_DOCUMENT_MANAGE,STUDENT_DOCUMENT_VIEW,ATTENDANCE_VIEW,ATTENDANCE_CORRECT,ATTENDANCE_LOCK,ATTENDANCE_UNLOCK,STAFF_ATTENDANCE_VIEW,FEE_VIEW,FEE_STRUCTURE_MANAGE,FEE_CATEGORY_MANAGE,EXAM_VIEW,EXAM_MANAGE,GRADING_SCALE_MANAGE,HR_VIEW,HR_STAFF_MANAGE,LIBRARY_VIEW,TRANSPORT_VIEW,COMMUNICATION_VIEW,ANNOUNCEMENT_CREATE,ANNOUNCEMENT_PUBLISH,ANNOUNCEMENT_CANCEL,NOTIFICATION_TEMPLATE_MANAGE,REPORT_VIEW,REPORT_CREATE,REPORT_MANAGE,REPORT_EXPORT','2026-10-07 16:06:21.520065','2026-10-07 16:06:21.520065',NULL,NULL,NULL),(41,'ROLE','Principal',NULL,'Academic leadership, approvals, and staff oversight',NULL,NULL,NULL,NULL,NULL,'ACADEMIC_CONFIGURATION_VIEW,CLASS_VIEW,GRADE_LEVEL_VIEW,SUBJECT_VIEW,TEACHER_ASSIGNMENT_VIEW,TEACHER_ASSIGNMENT_MANAGE,WORKING_CALENDAR_VIEW,STUDENT_VIEW,STUDENT_ENROLLMENT_MANAGE,STUDENT_TRANSFER_MANAGE,STUDENT_EXIT_MANAGE,ATTENDANCE_VIEW,ATTENDANCE_CORRECT,ATTENDANCE_LOCK,ATTENDANCE_UNLOCK,STAFF_ATTENDANCE_VIEW,EXAM_VIEW,EXAM_MANAGE,MARKS_APPROVE,MARKS_CORRECT_AFTER_PUBLISH,GRADING_SCALE_MANAGE,REPORT_CARD_PUBLISH,HR_VIEW,HR_LEAVE_MANAGE,GUARDIAN_VIEW,COMMUNICATION_VIEW,ANNOUNCEMENT_CREATE,ANNOUNCEMENT_PUBLISH,REPORT_VIEW,REPORT_CREATE,MEMBERSHIP_VIEW','2026-10-07 16:06:21.520065','2026-10-07 16:06:21.520065',NULL,NULL,NULL),(42,'ROLE','Academic Admin',NULL,'Day-to-day academic structure and exam configuration',NULL,NULL,NULL,NULL,NULL,'ACADEMIC_CONFIGURATION_MANAGE,ACADEMIC_CONFIGURATION_VIEW,CLASS_MANAGE,CLASS_VIEW,GRADE_LEVEL_MANAGE,GRADE_LEVEL_VIEW,SUBJECT_MANAGE,SUBJECT_VIEW,TEACHER_ASSIGNMENT_MANAGE,TEACHER_ASSIGNMENT_VIEW,WORKING_CALENDAR_MANAGE,WORKING_CALENDAR_VIEW,EXAM_VIEW,EXAM_MANAGE,MARKS_CORRECT,GRADING_SCALE_MANAGE,REPORT_CARD_PUBLISH,STUDENT_VIEW,ATTENDANCE_VIEW,COMMUNICATION_VIEW,ANNOUNCEMENT_CREATE','2026-10-07 16:06:21.520065','2026-10-07 16:06:21.520065',NULL,NULL,NULL),(43,'ROLE','Teacher',NULL,'Classroom attendance, marks entry, and guardian communication',NULL,NULL,NULL,NULL,NULL,'CLASS_VIEW,SUBJECT_VIEW,STUDENT_VIEW,STUDENT_DOCUMENT_VIEW,ATTENDANCE_MARK,ATTENDANCE_VIEW,EXAM_VIEW,MARKS_ENTER,MARKS_SUBMIT,GUARDIAN_VIEW,COMMUNICATION_VIEW,ANNOUNCEMENT_CREATE','2026-10-07 16:06:21.520065','2026-10-07 16:06:21.520065',NULL,NULL,NULL),(44,'ROLE','Accountant',NULL,'Fee collection, invoicing, and financial approvals',NULL,NULL,NULL,NULL,NULL,'FEE_VIEW,FEE_CATEGORY_MANAGE,FEE_STRUCTURE_MANAGE,FEE_INVOICE_MANAGE,FEE_COLLECT,FEE_REMINDER_SEND,FEE_ADJUSTMENT_MANAGE,FEE_REFUND_APPROVE,FEE_WAIVER_APPROVE,FEE_DISCOUNT_APPROVE,FEE_ASSIGNMENT_MANAGE,STUDENT_VIEW,REPORT_VIEW,REPORT_CREATE','2026-10-07 16:06:21.520065','2026-10-07 16:06:21.520065',NULL,NULL,'FEES'),(45,'ROLE','HR Manager',NULL,'Staff records, leave, and recruitment',NULL,NULL,NULL,NULL,NULL,'HR_VIEW,HR_STAFF_MANAGE,HR_LEAVE_MANAGE,HR_LEAVE_TYPE_MANAGE,HR_RECRUITMENT_MANAGE,HR_PAYROLL_VIEW,MEMBERSHIP_VIEW,PROFILE_CHANGE_MANAGE,REPORT_VIEW','2026-10-07 16:06:21.520065','2026-10-07 16:06:21.520065',NULL,NULL,'HR'),(46,'ROLE','Librarian',NULL,'Library catalog, borrowing, and fines',NULL,NULL,NULL,NULL,NULL,'LIBRARY_VIEW,LIBRARY_CATALOG_MANAGE,LIBRARY_BORROW_MANAGE,LIBRARY_FINE_MANAGE,STUDENT_VIEW','2026-10-07 16:06:21.520065','2026-10-07 16:06:21.520065',NULL,NULL,'LIBRARY'),(47,'ROLE','Transport Manager',NULL,'Vehicles, routes, and student transport assignments',NULL,NULL,NULL,NULL,NULL,'TRANSPORT_VIEW,TRANSPORT_ROUTE_MANAGE,TRANSPORT_VEHICLE_MANAGE,TRANSPORT_ASSIGNMENT_MANAGE,TRANSPORT_TRIP_LOG,STUDENT_VIEW','2026-10-07 16:06:21.520065','2026-10-07 16:06:21.520065',NULL,NULL,'TRANSPORT'),(48,'ROLE','Inventory Manager',NULL,'Item catalog, stock entries, and adjustments',NULL,NULL,NULL,NULL,NULL,'INVENTORY_VIEW,INVENTORY_CATALOG_MANAGE,INVENTORY_STOCK_MANAGE,INVENTORY_ADJUSTMENT_MANAGE,INVENTORY_SUPPLIER_MANAGE,INVENTORY_CUSTOMER_MANAGE','2026-10-07 16:06:21.520065','2026-10-07 16:06:21.520065',NULL,NULL,'INVENTORY'),(49,'ROLE','Purchase Manager',NULL,'Purchase orders and supplier returns',NULL,NULL,NULL,NULL,NULL,'PURCHASE_VIEW,PURCHASE_MANAGE,INVENTORY_VIEW','2026-10-07 16:06:21.520065','2026-10-07 16:06:21.520065',NULL,NULL,'PURCHASE'),(50,'ROLE','Receptionist',NULL,'Front-desk enquiries and visitor communication',NULL,NULL,NULL,NULL,NULL,'STUDENT_VIEW,GUARDIAN_VIEW,MEMBERSHIP_VIEW,ATTENDANCE_VIEW,FEE_VIEW,COMMUNICATION_VIEW,ANNOUNCEMENT_CREATE','2026-10-07 16:06:21.520065','2026-10-07 16:06:21.520065',NULL,NULL,NULL),(51,'ROLE','Staff',NULL,'Baseline read-only access for general staff',NULL,NULL,NULL,NULL,NULL,'STUDENT_VIEW,COMMUNICATION_VIEW,ATTENDANCE_VIEW','2026-10-07 16:06:21.520065','2026-10-07 16:06:21.520065',NULL,NULL,NULL),(52,'FEE_CATEGORY','Tuition Fee','TUITION','Recurring academic tuition charge',NULL,NULL,'GENERAL',NULL,NULL,NULL,'2026-10-07 16:06:21.520300','2026-10-07 16:06:21.520300',NULL,NULL,NULL),(53,'FEE_CATEGORY','Admission Fee','ADMISSION','One-time fee charged at admission',NULL,NULL,'GENERAL',NULL,NULL,NULL,'2026-10-07 16:06:21.520300','2026-10-07 16:06:21.520300',NULL,NULL,NULL),(54,'FEE_CATEGORY','Examination Fee','EXAMINATION','Per-exam or per-term examination charge',NULL,NULL,'GENERAL',NULL,NULL,NULL,'2026-10-07 16:06:21.520300','2026-10-07 16:06:21.520300',NULL,NULL,'EXAMINATIONS'),(55,'FEE_CATEGORY','Transport Fee','TRANSPORT','School transport charge',NULL,NULL,'TRANSPORT',NULL,NULL,NULL,'2026-10-07 16:06:21.520300','2026-10-07 16:06:21.520300',NULL,NULL,'TRANSPORT'),(56,'FEE_CATEGORY','Library Fee','LIBRARY','Library membership/usage charge',NULL,NULL,'GENERAL',NULL,NULL,NULL,'2026-10-07 16:06:21.520300','2026-10-07 16:06:21.520300',NULL,NULL,'LIBRARY'),(57,'FEE_CATEGORY','Other Fee','OTHER','Miscellaneous charges not covered above',NULL,NULL,'GENERAL',NULL,NULL,NULL,'2026-10-07 16:06:21.520300','2026-10-07 16:06:21.520300',NULL,NULL,NULL),(58,'ITEM_CATEGORY','Stationery','STATIONERY',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.520559','2026-10-07 16:06:21.520559',NULL,NULL,NULL),(59,'ITEM_CATEGORY','Furniture','FURNITURE',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.520559','2026-10-07 16:06:21.520559',NULL,NULL,NULL),(60,'ITEM_CATEGORY','Electronics','ELECTRONICS',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.520559','2026-10-07 16:06:21.520559',NULL,NULL,NULL),(61,'ITEM_CATEGORY','Cleaning Supplies','CLEANING_SUPPLIES',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.520559','2026-10-07 16:06:21.520559',NULL,NULL,NULL),(62,'ITEM_CATEGORY','Sports','SPORTS',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.520559','2026-10-07 16:06:21.520559',NULL,NULL,NULL),(63,'ITEM_CATEGORY','Laboratory','LABORATORY',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.520559','2026-10-07 16:06:21.520559',NULL,NULL,NULL),(64,'ITEM_CATEGORY','General','GENERAL',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-07 16:06:21.520559','2026-10-07 16:06:21.520559',NULL,NULL,NULL),(65,'GRADING_BAND','A+',NULL,NULL,0,NULL,NULL,90,'Outstanding',NULL,'2026-10-07 16:06:21.520807','2026-10-07 16:06:21.520807',NULL,NULL,NULL),(66,'GRADING_BAND','A',NULL,NULL,1,NULL,NULL,80,'Excellent',NULL,'2026-10-07 16:06:21.520807','2026-10-07 16:06:21.520807',NULL,NULL,NULL),(67,'GRADING_BAND','B+',NULL,NULL,2,NULL,NULL,70,'Very good',NULL,'2026-10-07 16:06:21.520807','2026-10-07 16:06:21.520807',NULL,NULL,NULL),(68,'GRADING_BAND','B',NULL,NULL,3,NULL,NULL,60,'Good',NULL,'2026-10-07 16:06:21.520807','2026-10-07 16:06:21.520807',NULL,NULL,NULL),(69,'GRADING_BAND','C+',NULL,NULL,4,NULL,NULL,50,'Above average',NULL,'2026-10-07 16:06:21.520807','2026-10-07 16:06:21.520807',NULL,NULL,NULL),(70,'GRADING_BAND','C',NULL,NULL,5,NULL,NULL,40,'Average',NULL,'2026-10-07 16:06:21.520807','2026-10-07 16:06:21.520807',NULL,NULL,NULL),(71,'GRADING_BAND','D',NULL,NULL,6,NULL,NULL,33,'Pass',NULL,'2026-10-07 16:06:21.520807','2026-10-07 16:06:21.520807',NULL,NULL,NULL),(72,'GRADING_BAND','F',NULL,NULL,7,NULL,NULL,0,'Fail',NULL,'2026-10-07 16:06:21.520807','2026-10-07 16:06:21.520807',NULL,NULL,NULL);
