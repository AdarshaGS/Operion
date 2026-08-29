package com.operion.transport;

import java.time.LocalDate;
import java.time.LocalTime;

import com.operion.finance.FeeService;
import com.operion.finance.FeeStructure;
import com.operion.finance.StudentFeeAssignment;
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
	private final FeeService feeService;

	public TransportService(VehicleRepository vehicleRepository, RouteRepository routeRepository,
			RouteStopRepository routeStopRepository, StudentTransportAssignmentRepository studentTransportAssignmentRepository,
			TripLogRepository tripLogRepository, FeeService feeService) {
		this.vehicleRepository = vehicleRepository;
		this.routeRepository = routeRepository;
		this.routeStopRepository = routeStopRepository;
		this.studentTransportAssignmentRepository = studentTransportAssignmentRepository;
		this.tripLogRepository = tripLogRepository;
		this.feeService = feeService;
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

	public StudentTransportAssignment assignStudent(StudentEnrollment studentEnrollment, Route route, RouteStop routeStop,
			boolean usesPickup, boolean usesDrop, LocalDate effectiveFrom) {
		return assignStudent(studentEnrollment, route, routeStop, usesPickup, usesDrop, effectiveFrom, null);
	}

	/**
	 * transportFeeStructure is optional - when supplied, it's linked via the existing Fees
	 * machinery (FeeService.assignFee) so the transport charge flows through the same
	 * invoice/payment/refund pipeline as every other fee, instead of a parallel one. Which
	 * FeeCategory/FeeStructure represents "transport" is entirely the caller's/tenant's
	 * choice - nothing here assumes a category name or code.
	 */
	@Transactional
	public StudentTransportAssignment assignStudent(StudentEnrollment studentEnrollment, Route route, RouteStop routeStop,
			boolean usesPickup, boolean usesDrop, LocalDate effectiveFrom, FeeStructure transportFeeStructure) {
		studentTransportAssignmentRepository.findByStudentEnrollmentIdAndStatus(studentEnrollment.getId(), TransportAssignmentStatus.ACTIVE)
				.ifPresent(existing -> {
					throw new IllegalStateException("Enrollment " + studentEnrollment.getId() + " already has an active transport assignment");
				});
		requireSameCampus(studentEnrollment, route);
		requireStopOnRoute(route, routeStop);
		requireVehicleCapacity(route);

		StudentTransportAssignment assignment = studentTransportAssignmentRepository.save(
				new StudentTransportAssignment(studentEnrollment, route, routeStop, usesPickup, usesDrop, effectiveFrom));

		if (transportFeeStructure != null) {
			StudentFeeAssignment feeAssignment = feeService.assignFee(studentEnrollment, transportFeeStructure, null, null, null);
			assignment.linkFeeAssignment(feeAssignment);
			assignment = studentTransportAssignmentRepository.save(assignment);
		}
		return assignment;
	}

	@Transactional
	public StudentTransportAssignment reassignRoute(StudentTransportAssignment assignment, Route route, RouteStop routeStop,
			LocalDate effectiveFrom) {
		requireSameCampus(assignment.getStudentEnrollment(), route);
		requireStopOnRoute(route, routeStop);
		assignment.end(effectiveFrom);
		studentTransportAssignmentRepository.save(assignment);

		requireVehicleCapacity(route);
		return studentTransportAssignmentRepository.save(new StudentTransportAssignment(
				assignment.getStudentEnrollment(), route, routeStop, assignment.isUsesPickup(), assignment.isUsesDrop(), effectiveFrom));
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

	private void requireVehicleCapacity(Route route) {
		Vehicle vehicle = route.getVehicle();
		if (vehicle == null) {
			return;
		}
		long activeCount = studentTransportAssignmentRepository.countByRouteVehicleIdAndStatus(vehicle.getId(), TransportAssignmentStatus.ACTIVE);
		if (activeCount + 1 > vehicle.getCapacity()) {
			throw new IllegalStateException("Vehicle " + vehicle.getId() + " is at capacity (" + vehicle.getCapacity() + ")");
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
