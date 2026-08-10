package com.operion.transport.api;

import java.util.List;

import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.transport.Route;
import com.operion.transport.RouteRepository;
import com.operion.transport.RouteStatus;
import com.operion.transport.RouteStop;
import com.operion.transport.RouteStopRepository;
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
@RequestMapping("/api/v1/transport/routes")
public class RouteController {

	private final TransportService transportService;
	private final RouteRepository routeRepository;
	private final RouteStopRepository routeStopRepository;
	private final CampusRepository campusRepository;
	private final VehicleRepository vehicleRepository;

	public RouteController(TransportService transportService, RouteRepository routeRepository, RouteStopRepository routeStopRepository,
			CampusRepository campusRepository, VehicleRepository vehicleRepository) {
		this.transportService = transportService;
		this.routeRepository = routeRepository;
		this.routeStopRepository = routeStopRepository;
		this.campusRepository = campusRepository;
		this.vehicleRepository = vehicleRepository;
	}

	@PostMapping
	public RouteResponse create(@RequestBody CreateRouteRequest request) {
		Campus campus = campusRepository.findById(request.campusId())
				.orElseThrow(() -> new IllegalArgumentException("No campus with id " + request.campusId()));
		Vehicle vehicle = findVehicle(request.vehicleId());
		return RouteResponse.from(transportService.createRoute(campus, request.name(), request.code(), vehicle));
	}

	@GetMapping
	public List<RouteResponse> list(@RequestParam(required = false) Long campusId) {
		List<Route> routes = campusId != null ? routeRepository.findByCampusId(campusId) : routeRepository.findAll();
		return routes.stream().map(RouteResponse::from).toList();
	}

	@PostMapping("/{id}/vehicle")
	public RouteResponse assignVehicle(@PathVariable Long id, @RequestBody AssignVehicleRequest request) {
		Route route = findRoute(id);
		return RouteResponse.from(transportService.assignVehicleToRoute(route, findVehicle(request.vehicleId())));
	}

	@PostMapping("/{id}/status")
	public RouteResponse changeStatus(@PathVariable Long id, @RequestBody ChangeRouteStatusRequest request) {
		Route route = findRoute(id);
		return RouteResponse.from(transportService.changeRouteStatus(route, RouteStatus.valueOf(request.status())));
	}

	@PostMapping("/{id}/stops")
	public RouteStopResponse addStop(@PathVariable Long id, @RequestBody AddRouteStopRequest request) {
		Route route = findRoute(id);
		RouteStop stop = transportService.addStop(route, request.stopName(), request.sequenceNumber(),
				request.pickupTime(), request.dropTime(), request.latitude(), request.longitude());
		return RouteStopResponse.from(stop);
	}

	@GetMapping("/{id}/stops")
	public List<RouteStopResponse> listStops(@PathVariable Long id) {
		return routeStopRepository.findByRouteIdOrderBySequenceNumber(id).stream().map(RouteStopResponse::from).toList();
	}

	private Route findRoute(Long id) {
		return routeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No route with id " + id));
	}

	private Vehicle findVehicle(Long id) {
		return id == null ? null : vehicleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No vehicle with id " + id));
	}
}
