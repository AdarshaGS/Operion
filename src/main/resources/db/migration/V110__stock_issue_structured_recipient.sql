-- #174: stock issue recipient is free text only; add optional structured links so an
-- issue can be attributed to a specific department, staff member (Person), or student
-- when that's the actual case. issued_to stays required free text for the common
-- non-individual case (a class, a lab) - these are additional, not a replacement.

ALTER TABLE stock_issues
    ADD COLUMN department_id BIGINT,
    ADD COLUMN recipient_person_id BIGINT,
    ADD COLUMN recipient_student_id BIGINT,
    ADD CONSTRAINT fk_stock_issues_department FOREIGN KEY (department_id) REFERENCES departments (id),
    ADD CONSTRAINT fk_stock_issues_recipient_person FOREIGN KEY (recipient_person_id) REFERENCES persons (id),
    ADD CONSTRAINT fk_stock_issues_recipient_student FOREIGN KEY (recipient_student_id) REFERENCES students (id);
