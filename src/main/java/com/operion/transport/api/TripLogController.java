package com.operion.transport.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.transport.Route;
import com.operion.transport.RouteRepository;
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
public class TripLogController {

	private final TransportService transportService;
	private final TripLogRepository tripLogRepository;
	private final RouteRepository routeRepository;
	private final VehicleRepository vehicleRepository;
	private final PersonRepository personRepository;

	public TripLogController(TransportService transportService, TripLogRepository tripLogRepository, RouteRepository routeRepository,
			VehicleRepository vehicleRepository, PersonRepository personRepository) {
		this.transportService = transportService;
		this.tripLogRepository = tripLogRepository;
		this.routeRepository = routeRepository;
		this.vehicleRepository = vehicleRepository;
		this.personRepository = personRepository;
	}

	@PostMapping
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
	public TripLogResponse start(@PathVariable Long id) {
		return TripLogResponse.from(transportService.startTrip(findTripLog(id)));
	}

	@PostMapping("/{id}/complete")
	public TripLogResponse complete(@PathVariable Long id, @RequestBody TripRemarksRequest request) {
		return TripLogResponse.from(transportService.completeTrip(findTripLog(id), request.remarks()));
	}

	@PostMapping("/{id}/cancel")
	public TripLogResponse cancel(@PathVariable Long id, @RequestBody TripRemarksRequest request) {
		return TripLogResponse.from(transportService.cancelTrip(findTripLog(id), request.remarks()));
	}

	private TripLog findTripLog(Long id) {
		return tripLogRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No trip log with id " + id));
	}
}
