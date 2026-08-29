-- Two-way messaging (#239): MessageThread (CLASS_GROUP auto-scoped to a Section, or
-- DIRECT between exactly two Persons), ThreadParticipant (membership + per-person read
-- position), Message. Distinct from the Communication module's Announcement/
-- NotificationRecipient tables - one-way broadcast fan-out vs a genuine conversation.

CREATE TABLE message_threads (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    type              VARCHAR(20) NOT NULL,
    section_id        BIGINT,
    last_message_at   DATETIME(6),
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_message_threads_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_message_threads_section FOREIGN KEY (section_id) REFERENCES sections (id)
) ENGINE = InnoDB;

-- One CLASS_GROUP thread per section - enforced here, not just in
-- MessagingService.getOrCreateClassGroupThread's get-or-create logic.
CREATE UNIQUE INDEX uq_message_threads_section_type ON message_threads (organisation_id, section_id, type);

CREATE TABLE thread_participants (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    thread_id         BIGINT NOT NULL,
    person_id         BIGINT NOT NULL,
    last_read_at      DATETIME(6),
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_thread_participants_thread_person UNIQUE (thread_id, person_id),
    CONSTRAINT fk_thread_participants_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_thread_participants_thread FOREIGN KEY (thread_id) REFERENCES message_threads (id),
    CONSTRAINT fk_thread_participants_person FOREIGN KEY (person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_thread_participants_person ON thread_participants (organisation_id, person_id);

CREATE TABLE messages (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    thread_id         BIGINT NOT NULL,
    sender_person_id  BIGINT NOT NULL,
    body              TEXT NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_messages_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_messages_thread FOREIGN KEY (thread_id) REFERENCES message_threads (id),
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_messages_thread_created ON messages (organisation_id, thread_id, created_at);
