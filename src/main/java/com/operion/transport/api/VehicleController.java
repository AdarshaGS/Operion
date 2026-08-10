package com.operion.transport.api;

import java.util.List;

import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.transport.TransportService;
import com.operion.transport.Vehicle;
import com.operion.transport.VehicleRepository;
import com.operion.transport.VehicleStatus;
import com.operion.transport.VehicleType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transport/vehicles")
public class VehicleController {

	private final TransportService transportService;
	private final VehicleRepository vehicleRepository;
	private final CampusRepository campusRepository;
	private final PersonRepository personRepository;

	public VehicleController(TransportService transportService, VehicleRepository vehicleRepository,
			CampusRepository campusRepository, PersonRepository personRepository) {
		this.transportService = transportService;
		this.vehicleRepository = vehicleRepository;
		this.campusRepository = campusRepository;
		this.personRepository = personRepository;
	}

	@PostMapping
	public VehicleResponse create(@RequestBody CreateVehicleRequest request) {
		Campus campus = findCampus(request.campusId());
		Person driver = findPerson(request.driverPersonId());
		Person attendant = findPerson(request.attendantPersonId());
		Vehicle vehicle = transportService.createVehicle(
				campus, request.registrationNumber(), VehicleType.valueOf(request.vehicleType()), request.capacity(), driver, attendant);
		return VehicleResponse.from(vehicle);
	}

	@GetMapping
	public List<VehicleResponse> list(@RequestParam(required = false) Long campusId) {
		List<Vehicle> vehicles = campusId != null ? vehicleRepository.findByCampusId(campusId) : vehicleRepository.findAll();
		return vehicles.stream().map(VehicleResponse::from).toList();
	}

	@PostMapping("/{id}/crew")
	public VehicleResponse reassignCrew(@PathVariable Long id, @RequestBody ReassignCrewRequest request) {
		Vehicle vehicle = findVehicle(id);
		Person driver = findPerson(request.driverPersonId());
		Person attendant = findPerson(request.attendantPersonId());
		return VehicleResponse.from(transportService.reassignCrew(vehicle, driver, attendant));
	}

	@PostMapping("/{id}/status")
	public VehicleResponse changeStatus(@PathVariable Long id, @RequestBody ChangeVehicleStatusRequest request) {
		Vehicle vehicle = findVehicle(id);
		return VehicleResponse.from(transportService.changeVehicleStatus(vehicle, VehicleStatus.valueOf(request.status())));
	}

	private Vehicle findVehicle(Long id) {
		return vehicleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No vehicle with id " + id));
	}

	private Campus findCampus(Long id) {
		return campusRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No campus with id " + id));
	}

	private Person findPerson(Long id) {
		return id == null ? null : personRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No person with id " + id));
	}
}
