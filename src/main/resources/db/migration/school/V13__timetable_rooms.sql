-- Rooms a timetable slot can be held in. A slot's room is optional (NULL = no room tracked), so
-- existing timetables are unchanged. A room clash is also enforced in the service across overlapping
-- periods; the unique key is the backstop for the same-period race.

CREATE TABLE rooms (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id  BIGINT NOT NULL,
    name             VARCHAR(100) NOT NULL,
    room_type        VARCHAR(50) NULL,
    capacity         INT NULL,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_rooms_org_name UNIQUE (organisation_id, name)
) ENGINE = InnoDB;

ALTER TABLE timetable_entries ADD COLUMN room_id BIGINT NULL;
ALTER TABLE timetable_entries ADD CONSTRAINT fk_timetable_room FOREIGN KEY (room_id) REFERENCES rooms (id);
ALTER TABLE timetable_entries ADD CONSTRAINT uq_timetable_room_slot UNIQUE (organisation_id, academic_year_id, room_id, day_of_week, period_id);
