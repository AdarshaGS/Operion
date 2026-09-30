-- #228: learning materials (notes, links, files) scoped to a class and, optionally, a subject.
-- Files live in the shared asset store (attachment_ref); a LINK row carries a url instead.

CREATE TABLE learning_materials (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id         BIGINT NOT NULL,
    school_class_id         BIGINT NOT NULL,
    subject_id              BIGINT NULL,
    title                   VARCHAR(250) NOT NULL,
    description             VARCHAR(2000) NULL,
    kind                    VARCHAR(20) NOT NULL,
    attachment_ref          VARCHAR(255) NULL,
    url                     VARCHAR(1000) NULL,
    uploaded_by_person_id   BIGINT NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    created_at              DATETIME(6) NOT NULL,
    updated_at              DATETIME(6) NOT NULL,
    created_by              BIGINT,
    updated_by              BIGINT,
    CONSTRAINT fk_learning_materials_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_learning_materials_class FOREIGN KEY (school_class_id) REFERENCES school_classes (id),
    CONSTRAINT fk_learning_materials_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_learning_materials_uploaded_by FOREIGN KEY (uploaded_by_person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_learning_materials_class ON learning_materials (organisation_id, school_class_id);
