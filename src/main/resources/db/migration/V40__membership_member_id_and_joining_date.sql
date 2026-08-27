-- Consolidated "Add member" flow (GitHub #104): employee/member ID and joining date
-- become optional fields on any member, not gated behind the HR-specific StaffProfile
-- flow (which keeps its own separate, required employee_code/date_of_joining tied to
-- designation/employment_type - those stay HR-only, unlike these two).
ALTER TABLE organisation_memberships
    ADD COLUMN member_id VARCHAR(255),
    ADD COLUMN joining_date DATE;
