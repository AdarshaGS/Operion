-- Driver-specific extension of Person (licence number + expiry), per #155. 1:1 with
-- Person - upserted as a whole on renewal, same "one row, update in place" convention
-- as staff_bank_details.

CREATE TABLE driver_profiles (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id       BIGINT NOT NULL,
    person_id             BIGINT NOT NULL,
    licence_number        VARCHAR(50) NOT NULL,
    licence_expiry_date   DATE NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    created_by            BIGINT,
    updated_by            BIGINT,
    CONSTRAINT uq_driver_profiles_person UNIQUE (person_id),
    CONSTRAINT fk_driver_profiles_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_driver_profiles_person FOREIGN KEY (person_id) REFERENCES persons (id)
) ENGINE = InnoDB;
