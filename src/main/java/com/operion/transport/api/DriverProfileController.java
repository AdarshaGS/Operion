package com.operion.transport.api;

import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.transport.DriverProfileRepository;
import com.operion.transport.TransportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Keyed on Person, not Vehicle - a driver's licence outlives any one vehicle
 * assignment, see DriverProfile's class doc. */
@RestController
@RequestMapping("/api/v1/transport/drivers")
@RequirePermission("TRANSPORT_VIEW")
public class DriverProfileController {

	private final TransportService transportService;
	private final DriverProfileRepository driverProfileRepository;
	private final PersonRepository personRepository;

	public DriverProfileController(TransportService transportService, DriverProfileRepository driverProfileRepository,
			PersonRepository personRepository) {
		this.transportService = transportService;
		this.driverProfileRepository = driverProfileRepository;
		this.personRepository = personRepository;
	}

	@GetMapping("/{personId}")
	public DriverProfileResponse get(@PathVariable Long personId) {
		return driverProfileRepository.findByPersonId(personId).map(DriverProfileResponse::from).orElse(null);
	}

	@PostMapping("/{personId}")
	@RequirePermission("TRANSPORT_VEHICLE_MANAGE")
	public DriverProfileResponse upsert(@PathVariable Long personId, @RequestBody UpsertDriverProfileRequest request) {
		Person person = personRepository.findById(personId).orElseThrow(() -> new IllegalArgumentException("No person with id " + personId));
		return DriverProfileResponse.from(transportService.upsertDriverProfile(person, request.licenceNumber(), request.licenceExpiryDate()));
	}
}
