ALTER TABLE organisation_memberships
    ADD COLUMN department_id BIGINT,
    ADD CONSTRAINT fk_memberships_department FOREIGN KEY (department_id) REFERENCES departments (id);
