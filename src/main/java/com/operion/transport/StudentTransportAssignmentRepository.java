package com.operion.transport;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentTransportAssignmentRepository extends JpaRepository<StudentTransportAssignment, Long> {

	Optional<StudentTransportAssignment> findByStudentEnrollmentIdAndStatus(Long studentEnrollmentId, TransportAssignmentStatus status);

	List<StudentTransportAssignment> findByRouteStopIdAndStatus(Long routeStopId, TransportAssignmentStatus status);

	List<StudentTransportAssignment> findByRouteIdAndStatus(Long routeId, TransportAssignmentStatus status);

	long countByStatus(TransportAssignmentStatus status);

	long countByRouteVehicleIdAndStatus(Long vehicleId, TransportAssignmentStatus status);
}
