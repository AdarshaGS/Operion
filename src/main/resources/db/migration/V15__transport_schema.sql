-- Transportation module schema: Vehicle, Route, RouteStop, StudentTransportAssignment,
-- TripLog. See ai-context/erp-system-plan.md §3.3 for the light sketch this deep-designed.
-- Real-time GPS ingestion is explicitly out of scope for v1 (different workload, evaluated
-- separately when actually built) - this is the registry + assignment + trip-log layer.

CREATE TABLE vehicles (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id       BIGINT NOT NULL,
    campus_id             BIGINT NOT NULL,
    registration_number   VARCHAR(30) NOT NULL,
    vehicle_type          VARCHAR(20) NOT NULL,
    capacity              INT NOT NULL,
    driver_person_id      BIGINT,
    attendant_person_id   BIGINT,
    status                VARCHAR(20) NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    created_by            BIGINT,
    updated_by            BIGINT,
    CONSTRAINT uq_vehicles_org_registration UNIQUE (organisation_id, registration_number),
    CONSTRAINT fk_vehicles_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_vehicles_campus FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_vehicles_driver FOREIGN KEY (driver_person_id) REFERENCES persons (id),
    CONSTRAINT fk_vehicles_attendant FOREIGN KEY (attendant_person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_vehicles_campus_status ON vehicles (organisation_id, campus_id, status);

CREATE TABLE routes (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    campus_id         BIGINT NOT NULL,
    name              VARCHAR(100) NOT NULL,
    code              VARCHAR(30) NOT NULL,
    vehicle_id        BIGINT,
    status            VARCHAR(20) NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_routes_org_campus_code UNIQUE (organisation_id, campus_id, code),
    CONSTRAINT fk_routes_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_routes_campus FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_routes_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id)
) ENGINE = InnoDB;

CREATE INDEX idx_routes_campus_status ON routes (organisation_id, campus_id, status);

CREATE TABLE route_stops (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    route_id          BIGINT NOT NULL,
    stop_name         VARCHAR(150) NOT NULL,
    sequence_number   INT NOT NULL,
    pickup_time       TIME,
    drop_time         TIME,
    latitude          DOUBLE,
    longitude         DOUBLE,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT uq_route_stops_route_sequence UNIQUE (route_id, sequence_number),
    CONSTRAINT fk_route_stops_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_route_stops_route FOREIGN KEY (route_id) REFERENCES routes (id)
) ENGINE = InnoDB;

CREATE TABLE student_transport_assignments (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id          BIGINT NOT NULL,
    student_enrollment_id    BIGINT NOT NULL,
    route_id                 BIGINT NOT NULL,
    route_stop_id            BIGINT NOT NULL,
    uses_pickup              BOOLEAN NOT NULL,
    uses_drop                BOOLEAN NOT NULL,
    status                   VARCHAR(20) NOT NULL,
    effective_from           DATE NOT NULL,
    effective_to             DATE,
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    created_by               BIGINT,
    updated_by               BIGINT,
    CONSTRAINT fk_transport_assignments_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_transport_assignments_enrollment FOREIGN KEY (student_enrollment_id) REFERENCES student_enrollments (id),
    CONSTRAINT fk_transport_assignments_route FOREIGN KEY (route_id) REFERENCES routes (id),
    CONSTRAINT fk_transport_assignments_route_stop FOREIGN KEY (route_stop_id) REFERENCES route_stops (id)
) ENGINE = InnoDB;

CREATE INDEX idx_transport_assignments_enrollment_status ON student_transport_assignments (organisation_id, student_enrollment_id, status);
CREATE INDEX idx_transport_assignments_route_stop ON student_transport_assignments (organisation_id, route_stop_id, status);

CREATE TABLE trip_logs (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    route_id          BIGINT NOT NULL,
    vehicle_id        BIGINT NOT NULL,
    driver_person_id  BIGINT,
    trip_date         DATE NOT NULL,
    trip_type         VARCHAR(20) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    started_at        DATETIME(6),
    completed_at      DATETIME(6),
    remarks           VARCHAR(500),
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT fk_trip_logs_organisation FOREIGN KEY (organisation_id) REFERENCES organisations (id),
    CONSTRAINT fk_trip_logs_route FOREIGN KEY (route_id) REFERENCES routes (id),
    CONSTRAINT fk_trip_logs_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT fk_trip_logs_driver FOREIGN KEY (driver_person_id) REFERENCES persons (id)
) ENGINE = InnoDB;

CREATE INDEX idx_trip_logs_route_date ON trip_logs (organisation_id, route_id, trip_date);
