package com.operion.transport.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.transport.BoardingStatus;
import com.operion.transport.Route;
import com.operion.transport.RouteRepository;
import com.operion.transport.StudentTripAttendanceRepository;
import com.operion.transport.TransportService.BoardingMark;
import com.operion.transport.TripLog;
import com.operion.transport.TripLogRepository;
import com.operion.transport.TripType;
import com.operion.transport.TransportService;
import com.operion.transport.Vehicle;
import com.operion.transport.VehicleRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transport/trip-logs")
@RequirePermission("TRANSPORT_VIEW")
public class TripLogController {

	private final TransportService transportService;
	private final TripLogRepository tripLogRepository;
	private final RouteRepository routeRepository;
	private final VehicleRepository vehicleRepository;
	private final PersonRepository personRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final StudentTripAttendanceRepository studentTripAttendanceRepository;

	public TripLogController(TransportService transportService, TripLogRepository tripLogRepository, RouteRepository routeRepository,
			VehicleRepository vehicleRepository, PersonRepository personRepository, StudentEnrollmentRepository studentEnrollmentRepository,
			StudentTripAttendanceRepository studentTripAttendanceRepository) {
		this.transportService = transportService;
		this.tripLogRepository = tripLogRepository;
		this.routeRepository = routeRepository;
		this.vehicleRepository = vehicleRepository;
		this.personRepository = personRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.studentTripAttendanceRepository = studentTripAttendanceRepository;
	}

	@PostMapping
	@RequirePermission("TRANSPORT_TRIP_LOG")
	public TripLogResponse schedule(@RequestBody ScheduleTripRequest request) {
		Route route = routeRepository.findById(request.routeId())
				.orElseThrow(() -> new IllegalArgumentException("No route with id " + request.routeId()));
		Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
				.orElseThrow(() -> new IllegalArgumentException("No vehicle with id " + request.vehicleId()));
		Person driver = request.driverPersonId() == null ? null : personRepository.findById(request.driverPersonId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.driverPersonId()));
		TripLog tripLog = transportService.scheduleTrip(route, vehicle, driver, request.tripDate(), TripType.valueOf(request.tripType()));
		return TripLogResponse.from(tripLog);
	}

	@GetMapping
	public List<TripLogResponse> byRouteAndDate(@RequestParam Long routeId, @RequestParam LocalDate tripDate) {
		return tripLogRepository.findByRouteIdAndTripDate(routeId, tripDate).stream().map(TripLogResponse::from).toList();
	}

	@PostMapping("/{id}/start")
	@RequirePermission("TRANSPORT_TRIP_LOG")
	public TripLogResponse start(@PathVariable Long id) {
		return TripLogResponse.from(transportService.startTrip(findTripLog(id)));
	}

	@PostMapping("/{id}/complete")
	@RequirePermission("TRANSPORT_TRIP_LOG")
	public TripLogResponse complete(@PathVariable Long id, @RequestBody TripRemarksRequest request) {
		return TripLogResponse.from(transportService.completeTrip(findTripLog(id), request.remarks()));
	}

	@PostMapping("/{id}/cancel")
	@RequirePermission("TRANSPORT_TRIP_LOG")
	public TripLogResponse cancel(@PathVariable Long id, @RequestBody TripRemarksRequest request) {
		return TripLogResponse.from(transportService.cancelTrip(findTripLog(id), request.remarks()));
	}

	@PostMapping("/{id}/boarding")
	@RequirePermission("TRANSPORT_TRIP_LOG")
	public List<StudentTripAttendanceResponse> markBoarding(@PathVariable Long id, @RequestBody MarkBoardingRequest request) {
		TripLog tripLog = findTripLog(id);
		List<BoardingMark> marks = request.marks().stream()
				.map(entry -> new BoardingMark(findEnrollment(entry.studentEnrollmentId()),
						BoardingStatus.valueOf(entry.boardingStatus()), entry.remarks()))
				.toList();
		return transportService.markBoarding(tripLog, marks).stream().map(StudentTripAttendanceResponse::from).toList();
	}

	@GetMapping("/{id}/boarding")
	public List<StudentTripAttendanceResponse> boarding(@PathVariable Long id) {
		return studentTripAttendanceRepository.findByTripLogId(id).stream().map(StudentTripAttendanceResponse::from).toList();
	}

	private StudentEnrollment findEnrollment(Long id) {
		return studentEnrollmentRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No student enrollment with id " + id));
	}

	private TripLog findTripLog(Long id) {
		return tripLogRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No trip log with id " + id));
	}
}
