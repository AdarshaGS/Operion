-- Per-student boarding record TripLog's own class doc deferred to v2 (#158) - same split
-- as ClassAttendanceRegister/StudentAttendance.

CREATE TABLE student_trip_attendances (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id          BIGINT NOT NULL,
    trip_log_id              BIGINT NOT NULL,
    student_enrollment_id    BIGINT NOT NULL,
    boarding_status          VARCHAR(20) NOT NULL,
    remarks                  VARCHAR(500),
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    created_by               BIGINT,
    updated_by               BIGINT,
    CONSTRAINT uq_trip_attendance_trip_enrollment UNIQUE (trip_log_id, student_enrollment_id),
    CONSTRAINT fk_trip_attendance_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_trip_attendance_trip_log FOREIGN KEY (trip_log_id) REFERENCES trip_logs (id),
    CONSTRAINT fk_trip_attendance_enrollment FOREIGN KEY (student_enrollment_id) REFERENCES student_enrollments (id)
) ENGINE = InnoDB;

CREATE INDEX idx_trip_attendance_trip ON student_trip_attendances (organisation_id, trip_log_id);
