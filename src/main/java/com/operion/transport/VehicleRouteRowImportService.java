package com.operion.transport;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One row, one transaction - same REQUIRES_NEW isolation as StudentRowImportService.
 * Throws on any row-level failure rather than catching internally; VehicleRouteImportService
 * is what converts the exception into an ImportRowResult.
 *
 * A row can create/resolve a Vehicle only, or a Vehicle plus an attached Route - see the
 * combined-row rule in the two natural-key duplicate checks below.
 */
@Service
public class VehicleRouteRowImportService {

	private final CampusRepository campusRepository;
	private final PersonRepository personRepository;
	private final VehicleRepository vehicleRepository;
	private final RouteRepository routeRepository;
	private final TransportService transportService;

	public VehicleRouteRowImportService(CampusRepository campusRepository, PersonRepository personRepository,
			VehicleRepository vehicleRepository, RouteRepository routeRepository, TransportService transportService) {
		this.campusRepository = campusRepository;
		this.personRepository = personRepository;
		this.vehicleRepository = vehicleRepository;
		this.routeRepository = routeRepository;
		this.transportService = transportService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ImportRowResult importRow(int rowNumber, Map<String, String> row, boolean validateOnly) {
		String campusName = require(row, "campus");
		String registrationNumber = require(row, "registrationNumber");
		VehicleType vehicleType = VehicleType.valueOf(require(row, "vehicleType").toUpperCase());
		int capacity = Integer.parseInt(require(row, "capacity"));

		String routeName = blankToNull(row.get("routeName"));
		String routeCode = blankToNull(row.get("routeCode"));
		if ((routeName == null) != (routeCode == null)) {
			throw new IllegalArgumentException("routeName and routeCode must both be given, or both left blank");
		}
		boolean hasRoute = routeCode != null;

		// Natural-key duplicate checks happen before anything is created, per row-import
		// convention - the route half takes priority: a taken routeCode duplicates the
		// whole row even if the vehicle side would otherwise be new.
		if (hasRoute && routeRepository.findByCodeIgnoreCase(routeCode).isPresent()) {
			return new ImportRowResult(rowNumber, ImportRowStatus.DUPLICATE, "Route with code '" + routeCode + "' already exists", null);
		}

		Optional<Vehicle> existingVehicle = vehicleRepository.findByRegistrationNumberIgnoreCase(registrationNumber);
		if (!hasRoute && existingVehicle.isPresent()) {
			return new ImportRowResult(rowNumber, ImportRowStatus.DUPLICATE,
					"Vehicle with registration number '" + registrationNumber + "' already exists", existingVehicle.get().getId());
		}

		Campus campus = campusRepository.findAll().stream().filter(c -> c.getName().equalsIgnoreCase(campusName)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No campus named '" + campusName + "'"));

		NameResolution driver = resolvePerson(blankToNull(row.get("driverName")));
		NameResolution attendant = resolvePerson(blankToNull(row.get("attendantName")));

		if (validateOnly) {
			return new ImportRowResult(rowNumber, ImportRowStatus.VALID, message("Valid", driver, attendant, null), null);
		}

		Vehicle vehicle = existingVehicle.orElseGet(
				() -> transportService.createVehicle(campus, registrationNumber, vehicleType, capacity, driver.person, attendant.person));

		if (!hasRoute) {
			return new ImportRowResult(rowNumber, ImportRowStatus.IMPORTED, message("Vehicle created", driver, attendant, null),
					vehicle.getId());
		}

		transportService.createRoute(campus, routeName, routeCode, vehicle);
		String base = existingVehicle.isPresent() ? "Route '" + routeCode + "' attached to existing vehicle"
				: "Vehicle and route created";
		return new ImportRowResult(rowNumber, ImportRowStatus.IMPORTED, message(base, driver, attendant, null), vehicle.getId());
	}

	private NameResolution resolvePerson(String fullName) {
		if (fullName == null) {
			return new NameResolution(null, null);
		}
		List<Person> matches = personRepository.findAll().stream().filter(p -> matchesFullName(p, fullName)).toList();
		if (matches.size() == 1) {
			return new NameResolution(matches.get(0), null);
		}
		String note = matches.isEmpty() ? "'" + fullName + "' not found, skipped"
				: "'" + fullName + "' matches " + matches.size() + " people, skipped";
		return new NameResolution(null, note);
	}

	private static boolean matchesFullName(Person person, String fullName) {
		String personFullName = person.getLastName() == null ? person.getFirstName() : person.getFirstName() + " " + person.getLastName();
		return personFullName.equalsIgnoreCase(fullName.trim());
	}

	private static String message(String base, NameResolution driver, NameResolution attendant, String extra) {
		StringBuilder message = new StringBuilder(base);
		if (driver.note != null) {
			message.append("; driver ").append(driver.note);
		}
		if (attendant.note != null) {
			message.append("; attendant ").append(attendant.note);
		}
		if (extra != null) {
			message.append("; ").append(extra);
		}
		return message.toString();
	}

	private record NameResolution(Person person, String note) {
	}

	private static String require(Map<String, String> row, String field) {
		String value = blankToNull(row.get(field));
		if (value == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
