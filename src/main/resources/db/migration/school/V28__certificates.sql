-- Certificates (transfer, bonafide, character, school leaving ...). A school's certificate types are its own editable
-- rows; certificate_type_presets is the shared starting catalog the School setup template copies from (never
-- referenced after the copy). Wording is plain text with {{token}} merge fields - substituted by the app, never
-- evaluated as a template expression. A request moves REQUESTED/PENDING_APPROVAL -> APPROVED -> ISSUED, and the
-- issued certificate itself is a row in issued_documents (kind CERTIFICATE) carrying the frozen snapshot.

CREATE TABLE certificate_type_presets (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                  VARCHAR(30) NOT NULL,
    name                  VARCHAR(100) NOT NULL,
    title                 VARCHAR(150) NOT NULL,
    body_template         TEXT NOT NULL,
    particulars_template  TEXT NULL,
    manual_fields         TEXT NULL,
    number_format         VARCHAR(60) NOT NULL,
    once_per_student      BIT NOT NULL,
    requires_exit         BIT NOT NULL,
    requires_clearance    BIT NOT NULL,
    approval_required     BIT NOT NULL,
    guardian_requestable  BIT NOT NULL,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    created_by            BIGINT,
    updated_by            BIGINT,
    CONSTRAINT uq_certificate_type_presets_code UNIQUE (code)
) ENGINE = InnoDB;

CREATE TABLE certificate_types (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id       BIGINT NOT NULL,
    code                  VARCHAR(30) NOT NULL,
    name                  VARCHAR(100) NOT NULL,
    title                 VARCHAR(150) NOT NULL,
    body_template         TEXT NOT NULL,
    particulars_template  TEXT NULL,
    manual_fields         TEXT NULL,
    number_format         VARCHAR(60) NOT NULL,
    next_sequence         BIGINT NOT NULL DEFAULT 1,
    page_size             VARCHAR(20) NOT NULL DEFAULT 'A4',
    once_per_student      BIT NOT NULL,
    requires_exit         BIT NOT NULL,
    requires_clearance    BIT NOT NULL,
    approval_required     BIT NOT NULL,
    guardian_requestable  BIT NOT NULL,
    active                BIT NOT NULL DEFAULT 1,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    created_by            BIGINT,
    updated_by            BIGINT,
    CONSTRAINT uq_certificate_types_org_code UNIQUE (organisation_id, code)
) ENGINE = InnoDB;

CREATE TABLE certificate_requests (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id             BIGINT NOT NULL,
    certificate_type_id         BIGINT NOT NULL,
    student_id                  BIGINT NOT NULL,
    status                      VARCHAR(20) NOT NULL,
    purpose                     VARCHAR(255) NULL,
    manual_values               TEXT NULL,
    requested_via               VARCHAR(10) NOT NULL,
    approved_by                 BIGINT NULL,
    approved_at                 DATETIME(6) NULL,
    rejected_by                 BIGINT NULL,
    rejection_reason            VARCHAR(500) NULL,
    clearance_override_reason   VARCHAR(500) NULL,
    clearance_overridden_by     BIGINT NULL,
    issued_document_id          BIGINT NULL,
    certificate_number          VARCHAR(60) NULL,
    verification_token          VARCHAR(40) NULL,
    issued_at                   DATETIME(6) NULL,
    issued_by                   BIGINT NULL,
    created_at                  DATETIME(6) NOT NULL,
    updated_at                  DATETIME(6) NOT NULL,
    created_by                  BIGINT,
    updated_by                  BIGINT,
    CONSTRAINT fk_certificate_requests_type FOREIGN KEY (certificate_type_id) REFERENCES certificate_types (id),
    CONSTRAINT fk_certificate_requests_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_certificate_requests_document FOREIGN KEY (issued_document_id) REFERENCES issued_documents (id),
    CONSTRAINT uq_certificate_requests_token UNIQUE (verification_token)
) ENGINE = InnoDB;

CREATE INDEX idx_certificate_requests_student ON certificate_requests (organisation_id, student_id);
CREATE INDEX idx_certificate_requests_status ON certificate_requests (organisation_id, status);

INSERT INTO certificate_type_presets (code, name, title, body_template, particulars_template, manual_fields, number_format,
    once_per_student, requires_exit, requires_clearance, approval_required, guardian_requestable, created_at, updated_at)
VALUES
('TC', 'Transfer Certificate', 'TRANSFER CERTIFICATE',
 'Certified that the above particulars are as recorded in the admission register of {{school.name}}.',
 'Name of the student | {{student.name}}\nAdmission number | {{student.admissionNumber}}\nName of parent / guardian | {{guardian.name}}\nDate of birth | {{student.dateOfBirth}}\nDate of birth (in words) | {{student.dateOfBirthInWords}}\nNationality | {{student.nationality}}\nDate of admission | {{student.admissionDate}}\nClass at admission | {{class.admitted}}\nClass last studied | {{class.current}}\nAcademic year | {{academicYear}}\nWorking days / days present | {{attendance.workingDays}} / {{attendance.daysPresent}}\nDate of leaving | {{exit.date}}\nReason for leaving | {{exit.reason}}\nSchool joining (if known) | {{exit.destinationSchool}}\nGeneral conduct | {{manual.conduct}}\nRemarks | {{manual.remarks}}',
 'conduct | General conduct\nremarks | Remarks',
 'TC-{YYYY}-{SEQ:4}', 1, 1, 1, 1, 0, NOW(6), NOW(6)),
('LEAVING', 'School Leaving Certificate', 'SCHOOL LEAVING CERTIFICATE',
 'This is to certify that {{student.name}} (admission number {{student.admissionNumber}}) was a student of {{school.name}} from {{student.admissionDate}} to {{exit.date}}, and last studied in {{class.current}}.\n\nDate of birth as per school records: {{student.dateOfBirth}} ({{student.dateOfBirthInWords}}).',
 NULL, NULL,
 'SLC-{YYYY}-{SEQ:4}', 1, 1, 1, 0, 0, NOW(6), NOW(6)),
('BONAFIDE', 'Bonafide Certificate', 'BONAFIDE CERTIFICATE',
 'This is to certify that {{student.name}} (admission number {{student.admissionNumber}}) is a bonafide student of {{school.name}}, studying in {{class.current}} during the academic year {{academicYear}}.\n\nDate of birth as per school records: {{student.dateOfBirth}}.\n\nThis certificate is issued on request for the purpose of {{purpose}}.',
 NULL, NULL,
 'BC-{YYYY}-{SEQ:4}', 0, 0, 0, 0, 1, NOW(6), NOW(6)),
('CHARACTER', 'Character Certificate', 'CHARACTER CERTIFICATE',
 'This is to certify that {{student.name}} (admission number {{student.admissionNumber}}) has been a student of {{school.name}} since {{student.admissionDate}} and is presently in {{class.current}}.\n\nTo the best of our knowledge, the student''s conduct and character have been {{manual.conduct}}.',
 NULL, 'conduct | Conduct and character',
 'CC-{YYYY}-{SEQ:4}', 0, 0, 0, 0, 0, NOW(6), NOW(6));

-- School setup template rows (category CERTIFICATE_TYPE): the code points at the preset above.
INSERT INTO school_template_master_items (category, name, code, description, module_code, created_at, updated_at)
VALUES
('CERTIFICATE_TYPE', 'Transfer Certificate', 'TC', 'Issued on exit; needs dues clearance and approval', 'STUDENTS', NOW(6), NOW(6)),
('CERTIFICATE_TYPE', 'Bonafide Certificate', 'BONAFIDE', 'Proof of current enrolment; guardians can request it', 'STUDENTS', NOW(6), NOW(6)),
('CERTIFICATE_TYPE', 'Character Certificate', 'CHARACTER', 'Conduct and character statement', 'STUDENTS', NOW(6), NOW(6)),
('CERTIFICATE_TYPE', 'School Leaving Certificate', 'LEAVING', 'Issued on exit; needs dues clearance', 'STUDENTS', NOW(6), NOW(6));

INSERT IGNORE INTO school_template_items (organisation_id, category, name, code, description, module_code, created_at, updated_at)
SELECT o.organisation_id, m.category, m.name, m.code, m.description, m.module_code, NOW(6), NOW(6)
FROM (SELECT DISTINCT organisation_id FROM school_template_items) o
JOIN school_template_master_items m ON m.category = 'CERTIFICATE_TYPE';

INSERT IGNORE INTO permissions (code, module, description, created_at, updated_at) VALUES
    ('CERTIFICATE_VIEW', 'certificate', 'View certificate requests and the issued-certificate register', NOW(6), NOW(6)),
    ('CERTIFICATE_REQUEST', 'certificate', 'Raise a certificate request for a student', NOW(6), NOW(6)),
    ('CERTIFICATE_APPROVE', 'certificate', 'Approve or reject certificate requests that need approval (not your own)', NOW(6), NOW(6)),
    ('CERTIFICATE_ISSUE', 'certificate', 'Issue and print approved certificates, including overriding dues clearance with a reason', NOW(6), NOW(6)),
    ('CERTIFICATE_CANCEL', 'certificate', 'Cancel an issued certificate', NOW(6), NOW(6)),
    ('CERTIFICATE_TYPE_MANAGE', 'certificate', 'Create and edit certificate types and their wording', NOW(6), NOW(6));

-- Default role templates pick the new permissions up (both the shared catalog and each school's own copy; roles
-- already created from the template are left alone - an admin grants these deliberately).
UPDATE school_template_master_items
SET permission_codes = CONCAT(permission_codes, ',CERTIFICATE_VIEW,CERTIFICATE_REQUEST,CERTIFICATE_ISSUE,CERTIFICATE_CANCEL,CERTIFICATE_TYPE_MANAGE')
WHERE category = 'ROLE' AND name = 'Administrator' AND permission_codes NOT LIKE '%CERTIFICATE_%';
UPDATE school_template_master_items
SET permission_codes = CONCAT(permission_codes, ',CERTIFICATE_VIEW,CERTIFICATE_REQUEST,CERTIFICATE_APPROVE')
WHERE category = 'ROLE' AND name = 'Principal' AND permission_codes NOT LIKE '%CERTIFICATE_%';
UPDATE school_template_master_items
SET permission_codes = CONCAT(permission_codes, ',CERTIFICATE_VIEW,CERTIFICATE_REQUEST')
WHERE category = 'ROLE' AND name = 'Receptionist' AND permission_codes NOT LIKE '%CERTIFICATE_%';

UPDATE school_template_items
SET permission_codes = CONCAT(permission_codes, ',CERTIFICATE_VIEW,CERTIFICATE_REQUEST,CERTIFICATE_ISSUE,CERTIFICATE_CANCEL,CERTIFICATE_TYPE_MANAGE')
WHERE category = 'ROLE' AND name = 'Administrator' AND permission_codes IS NOT NULL AND permission_codes NOT LIKE '%CERTIFICATE_%';
UPDATE school_template_items
SET permission_codes = CONCAT(permission_codes, ',CERTIFICATE_VIEW,CERTIFICATE_REQUEST,CERTIFICATE_APPROVE')
WHERE category = 'ROLE' AND name = 'Principal' AND permission_codes IS NOT NULL AND permission_codes NOT LIKE '%CERTIFICATE_%';
UPDATE school_template_items
SET permission_codes = CONCAT(permission_codes, ',CERTIFICATE_VIEW,CERTIFICATE_REQUEST')
WHERE category = 'ROLE' AND name = 'Receptionist' AND permission_codes IS NOT NULL AND permission_codes NOT LIKE '%CERTIFICATE_%';
