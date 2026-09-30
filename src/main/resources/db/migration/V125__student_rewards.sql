-- #231: rewards a teacher grants to a student (points + a reason). Feeds the REWARD leaderboard track.

CREATE TABLE student_rewards (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id       BIGINT NOT NULL,
    student_id            BIGINT NOT NULL,
    title                 VARCHAR(200) NOT NULL,
    points                INT NOT NULL,
    reason                VARCHAR(1000) NULL,
    granted_by_person_id  BIGINT NOT NULL,
    granted_at            DATETIME(6) NOT NULL,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    created_by            BIGINT,
    updated_by            BIGINT,
    CONSTRAINT fk_student_rewards_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_student_rewards_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_student_rewards_granter FOREIGN KEY (granted_by_person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_student_rewards_student ON student_rewards (organisation_id, student_id);
