package com.operion.transport.api;

import java.util.Comparator;
import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.finance.FeeStructure;
import com.operion.finance.FeeStructureRepository;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.transport.Route;
import com.operion.transport.RouteRepository;
import com.operion.transport.RouteStop;
import com.operion.transport.RouteStopRepository;
import com.operion.transport.StudentTransportAssignment;
import com.operion.transport.StudentTransportAssignmentRepository;
import com.operion.transport.TransportAssignmentStatus;
import com.operion.transport.TransportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transport/assignments")
@RequirePermission("TRANSPORT_VIEW")
public class StudentTransportAssignmentController {

	private final TransportService transportService;
	private final StudentTransportAssignmentRepository studentTransportAssignmentRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final RouteRepository routeRepository;
	private final RouteStopRepository routeStopRepository;
	private final FeeStructureRepository feeStructureRepository;

	public StudentTransportAssignmentController(TransportService transportService,
			StudentTransportAssignmentRepository studentTransportAssignmentRepository,
			StudentEnrollmentRepository studentEnrollmentRepository, RouteRepository routeRepository,
			RouteStopRepository routeStopRepository, FeeStructureRepository feeStructureRepository) {
		this.transportService = transportService;
		this.studentTransportAssignmentRepository = studentTransportAssignmentRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.routeRepository = routeRepository;
		this.routeStopRepository = routeStopRepository;
		this.feeStructureRepository = feeStructureRepository;
	}

	@PostMapping
	@RequirePermission("TRANSPORT_ASSIGNMENT_MANAGE")
	public StudentTransportAssignmentResponse create(@RequestBody CreateStudentTransportAssignmentRequest request) {
		StudentEnrollment enrollment = findEnrollment(request.studentEnrollmentId());
		Route route = findRoute(request.routeId());
		RouteStop routeStop = findRouteStop(request.routeStopId());
		FeeStructure feeStructure = request.feeStructureId() == null ? null : findFeeStructure(request.feeStructureId());
		StudentTransportAssignment assignment = transportService.assignStudent(
				enrollment, route, routeStop, request.usesPickup(), request.usesDrop(), request.effectiveFrom(), feeStructure);
		return StudentTransportAssignmentResponse.from(assignment);
	}

	@GetMapping
	public List<StudentTransportAssignmentResponse> byEnrollment(@RequestParam Long studentEnrollmentId) {
		return studentTransportAssignmentRepository.findByStudentEnrollmentIdAndStatus(studentEnrollmentId, TransportAssignmentStatus.ACTIVE)
				.map(StudentTransportAssignmentResponse::from)
				.map(List::of)
				.orElseGet(List::of);
	}

	@GetMapping("/by-route-stop")
	public List<StudentTransportAssignmentResponse> byRouteStop(@RequestParam Long routeStopId) {
		return studentTransportAssignmentRepository.findByRouteStopIdAndStatus(routeStopId, TransportAssignmentStatus.ACTIVE).stream()
				.map(StudentTransportAssignmentResponse::from)
				.toList();
	}

	@GetMapping("/by-route")
	public List<RouteRosterEntryResponse> byRoute(@RequestParam Long routeId) {
		return studentTransportAssignmentRepository.findByRouteIdAndStatus(routeId, TransportAssignmentStatus.ACTIVE).stream()
				.map(RouteRosterEntryResponse::from)
				.sorted(Comparator.comparingInt(RouteRosterEntryResponse::sequenceNumber).thenComparing(RouteRosterEntryResponse::studentName))
				.toList();
	}

	@PostMapping("/{id}/reassign")
	@RequirePermission("TRANSPORT_ASSIGNMENT_MANAGE")
	public StudentTransportAssignmentResponse reassignRoute(@PathVariable Long id, @RequestBody ReassignRouteRequest request) {
		StudentTransportAssignment assignment = findAssignment(id);
		return StudentTransportAssignmentResponse.from(transportService.reassignRoute(
				assignment, findRoute(request.routeId()), findRouteStop(request.routeStopId()), request.effectiveFrom()));
	}

	@PostMapping("/{id}/legs")
	@RequirePermission("TRANSPORT_ASSIGNMENT_MANAGE")
	public StudentTransportAssignmentResponse updateLegs(@PathVariable Long id, @RequestBody UpdateAssignmentLegsRequest request) {
		StudentTransportAssignment assignment = findAssignment(id);
		return StudentTransportAssignmentResponse.from(
				transportService.updateAssignmentLegs(assignment, request.usesPickup(), request.usesDrop()));
	}

	@PostMapping("/{id}/end")
	@RequirePermission("TRANSPORT_ASSIGNMENT_MANAGE")
	public StudentTransportAssignmentResponse end(@PathVariable Long id, @RequestBody EndAssignmentRequest request) {
		StudentTransportAssignment assignment = findAssignment(id);
		return StudentTransportAssignmentResponse.from(transportService.endAssignment(assignment, request.effectiveTo()));
	}

	private StudentTransportAssignment findAssignment(Long id) {
		return studentTransportAssignmentRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No transport assignment with id " + id));
	}

	private StudentEnrollment findEnrollment(Long id) {
		return studentEnrollmentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No student enrollment with id " + id));
	}

	private Route findRoute(Long id) {
		return routeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No route with id " + id));
	}

	private RouteStop findRouteStop(Long id) {
		return routeStopRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No route stop with id " + id));
	}

	private FeeStructure findFeeStructure(Long id) {
		return feeStructureRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No fee structure with id " + id));
	}
}
