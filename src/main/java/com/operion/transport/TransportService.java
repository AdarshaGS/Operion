package com.operion.transport;

import java.time.LocalDate;
import java.time.LocalTime;

import com.operion.identity.Person;
import com.operion.organisation.Campus;
import com.operion.student.StudentEnrollment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the vehicle/route/stop registry, student transport assignment lifecycle, and
 * trip logs. Enforces one ACTIVE assignment per StudentEnrollment (service-level, same
 * convention as StudentEnrollment.is_current / StudentGuardian.is_primary_guardian)
 * and that a route belongs to the same campus as the student's current class - a
 * student shouldn't ride a route registered to a different campus.
 */
@Service
public class TransportService {

	private final VehicleRepository vehicleRepository;
	private final RouteRepository routeRepository;
	private final RouteStopRepository routeStopRepository;
	private final StudentTransportAssignmentRepository studentTransportAssignmentRepository;
	private final TripLogRepository tripLogRepository;

	public TransportService(VehicleRepository vehicleRepository, RouteRepository routeRepository,
			RouteStopRepository routeStopRepository, StudentTransportAssignmentRepository studentTransportAssignmentRepository,
			TripLogRepository tripLogRepository) {
		this.vehicleRepository = vehicleRepository;
		this.routeRepository = routeRepository;
		this.routeStopRepository = routeStopRepository;
		this.studentTransportAssignmentRepository = studentTransportAssignmentRepository;
		this.tripLogRepository = tripLogRepository;
	}

	public Vehicle createVehicle(Campus campus, String registrationNumber, VehicleType vehicleType, int capacity,
			Person driver, Person attendant) {
		return vehicleRepository.save(new Vehicle(campus, registrationNumber, vehicleType, capacity, driver, attendant));
	}

	public Vehicle reassignCrew(Vehicle vehicle, Person driver, Person attendant) {
		vehicle.reassignCrew(driver, attendant);
		return vehicleRepository.save(vehicle);
	}

	public Vehicle changeVehicleStatus(Vehicle vehicle, VehicleStatus status) {
		vehicle.changeStatus(status);
		return vehicleRepository.save(vehicle);
	}

	public Route createRoute(Campus campus, String name, String code, Vehicle vehicle) {
		return routeRepository.save(new Route(campus, name, code, vehicle));
	}

	public Route assignVehicleToRoute(Route route, Vehicle vehicle) {
		route.assignVehicle(vehicle);
		return routeRepository.save(route);
	}

	public Route changeRouteStatus(Route route, RouteStatus status) {
		route.changeStatus(status);
		return routeRepository.save(route);
	}

	public RouteStop addStop(Route route, String stopName, int sequenceNumber, LocalTime pickupTime, LocalTime dropTime,
			Double latitude, Double longitude) {
		return routeStopRepository.save(new RouteStop(route, stopName, sequenceNumber, pickupTime, dropTime, latitude, longitude));
	}

	@Transactional
	public StudentTransportAssignment assignStudent(StudentEnrollment studentEnrollment, Route route, RouteStop routeStop,
			boolean usesPickup, boolean usesDrop, LocalDate effectiveFrom) {
		studentTransportAssignmentRepository.findByStudentEnrollmentIdAndStatus(studentEnrollment.getId(), TransportAssignmentStatus.ACTIVE)
				.ifPresent(existing -> {
					throw new IllegalStateException("Enrollment " + studentEnrollment.getId() + " already has an active transport assignment");
				});
		requireSameCampus(studentEnrollment, route);
		requireStopOnRoute(route, routeStop);

		return studentTransportAssignmentRepository.save(
				new StudentTransportAssignment(studentEnrollment, route, routeStop, usesPickup, usesDrop, effectiveFrom));
	}

	@Transactional
	public StudentTransportAssignment reassignRoute(StudentTransportAssignment assignment, Route route, RouteStop routeStop) {
		requireSameCampus(assignment.getStudentEnrollment(), route);
		requireStopOnRoute(route, routeStop);
		assignment.reassignRoute(route, routeStop);
		return studentTransportAssignmentRepository.save(assignment);
	}

	public StudentTransportAssignment updateAssignmentLegs(StudentTransportAssignment assignment, boolean usesPickup, boolean usesDrop) {
		assignment.updateLegs(usesPickup, usesDrop);
		return studentTransportAssignmentRepository.save(assignment);
	}

	public StudentTransportAssignment endAssignment(StudentTransportAssignment assignment, LocalDate effectiveTo) {
		assignment.end(effectiveTo);
		return studentTransportAssignmentRepository.save(assignment);
	}

	public TripLog scheduleTrip(Route route, Vehicle vehicle, Person driver, LocalDate tripDate, TripType tripType) {
		return tripLogRepository.save(new TripLog(route, vehicle, driver, tripDate, tripType));
	}

	public TripLog startTrip(TripLog tripLog) {
		tripLog.start();
		return tripLogRepository.save(tripLog);
	}

	public TripLog completeTrip(TripLog tripLog, String remarks) {
		tripLog.complete(remarks);
		return tripLogRepository.save(tripLog);
	}

	public TripLog cancelTrip(TripLog tripLog, String remarks) {
		tripLog.cancel(remarks);
		return tripLogRepository.save(tripLog);
	}

	private void requireStopOnRoute(Route route, RouteStop routeStop) {
		if (!routeStop.getRoute().getId().equals(route.getId())) {
			throw new IllegalArgumentException("Stop " + routeStop.getId() + " does not belong to route " + route.getId());
		}
	}

	private void requireSameCampus(StudentEnrollment studentEnrollment, Route route) {
		Campus enrollmentCampus = studentEnrollment.getSection().getSchoolClass().getCampus();
		if (!enrollmentCampus.getId().equals(route.getCampus().getId())) {
			throw new IllegalArgumentException(
					"Route " + route.getId() + " belongs to a different campus than the student's enrollment");
		}
	}
}
