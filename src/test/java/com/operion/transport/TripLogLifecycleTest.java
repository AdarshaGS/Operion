package com.operion.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.finance.FeeService;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves TripLog's SCHEDULED -> IN_PROGRESS -> COMPLETED lifecycle, that cancel is
 * blocked once a trip is COMPLETED, and that vehicle/driver are snapshotted at
 * creation (not re-read from the route's current default) - see TripLog's class doc.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, TransportService.class, FeeService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TripLogLifecycleTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private RouteRepository routeRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private TransportService transportService;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(Route route, Vehicle vehicle, Person driver, Vehicle substituteVehicle) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Person driver = personRepository.save(new Person("Ramesh", "Kumar"));
		Vehicle vehicle = vehicleRepository.save(new Vehicle(campus, "KA-01-1234", VehicleType.BUS, 40, driver, null));
		Vehicle substituteVehicle = vehicleRepository.save(new Vehicle(campus, "KA-01-5678", VehicleType.BUS, 40, null, null));
		Route route = routeRepository.save(new Route(campus, "Route 12", "R12", vehicle));

		return new Fixture(route, vehicle, driver, substituteVehicle);
	}

	@Test
	void tripProgressesFromScheduledToCompleted() {
		Fixture fixture = setUpFixture("trip-log-lifecycle");

		TripLog trip = transportService.scheduleTrip(fixture.route(), fixture.vehicle(), fixture.driver(), LocalDate.of(2025, 7, 1), TripType.PICKUP);
		assertThat(trip.getStatus()).isEqualTo(TripStatus.SCHEDULED);

		transportService.startTrip(trip);
		assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
		assertThat(trip.getStartedAt()).isNotNull();

		transportService.completeTrip(trip, "All students dropped");
		assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
		assertThat(trip.getCompletedAt()).isNotNull();
		assertThat(trip.getRemarks()).isEqualTo("All students dropped");
	}

	@Test
	void completingATripThatWasNeverStartedIsRejected() {
		Fixture fixture = setUpFixture("trip-log-complete-without-start");
		TripLog trip = transportService.scheduleTrip(fixture.route(), fixture.vehicle(), fixture.driver(), LocalDate.of(2025, 7, 1), TripType.DROP);

		assertThatThrownBy(() -> transportService.completeTrip(trip, "done")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cancellingACompletedTripIsRejected() {
		Fixture fixture = setUpFixture("trip-log-cancel-after-complete");
		TripLog trip = transportService.scheduleTrip(fixture.route(), fixture.vehicle(), fixture.driver(), LocalDate.of(2025, 7, 1), TripType.PICKUP);
		transportService.startTrip(trip);
		transportService.completeTrip(trip, "done");

		assertThatThrownBy(() -> transportService.cancelTrip(trip, "too late")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cancellingAScheduledTripIsAllowed() {
		Fixture fixture = setUpFixture("trip-log-cancel-scheduled");
		TripLog trip = transportService.scheduleTrip(fixture.route(), fixture.vehicle(), fixture.driver(), LocalDate.of(2025, 7, 1), TripType.PICKUP);

		transportService.cancelTrip(trip, "vehicle breakdown");

		assertThat(trip.getStatus()).isEqualTo(TripStatus.CANCELLED);
		assertThat(trip.getRemarks()).isEqualTo("vehicle breakdown");
	}

	@Test
	void tripSnapshotsTheCoveringVehicleIndependentlyOfTheRoutesDefault() {
		Fixture fixture = setUpFixture("trip-log-substitute-vehicle");

		TripLog trip = transportService.scheduleTrip(
				fixture.route(), fixture.substituteVehicle(), null, LocalDate.of(2025, 7, 1), TripType.PICKUP);

		assertThat(trip.getVehicle().getId()).isEqualTo(fixture.substituteVehicle().getId());
		assertThat(trip.getRoute().getVehicle().getId()).isEqualTo(fixture.vehicle().getId());
	}
}
