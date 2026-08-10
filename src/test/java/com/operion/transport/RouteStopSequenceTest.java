package com.operion.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the (route_id, sequence_number) uniqueness constraint on RouteStop and that
 * TransportService.addStop happily appends stops in order via the same route.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, TransportService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RouteStopSequenceTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private RouteRepository routeRepository;

	@Autowired
	private RouteStopRepository routeStopRepository;

	@Autowired
	private TransportService transportService;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private Route setUpRoute(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		return routeRepository.save(new Route(campus, "Route 12", "R12", null));
	}

	@Test
	void stopsCanBeAddedInSequence() {
		Route route = setUpRoute("route-stop-sequence");

		transportService.addStop(route, "Gate 1", 1, LocalTime.of(7, 0), LocalTime.of(15, 0), null, null);
		transportService.addStop(route, "Gate 2", 2, LocalTime.of(7, 10), LocalTime.of(15, 10), null, null);

		assertThat(routeStopRepository.findByRouteIdOrderBySequenceNumber(route.getId())).hasSize(2)
				.extracting(RouteStop::getSequenceNumber).containsExactly(1, 2);
	}

	@Test
	void duplicateSequenceNumberOnTheSameRouteIsRejected() {
		Route route = setUpRoute("route-stop-duplicate-sequence");
		transportService.addStop(route, "Gate 1", 1, null, null, null, null);

		assertThatThrownBy(() -> {
			transportService.addStop(route, "Gate 1 Duplicate", 1, null, null, null, null);
			routeStopRepository.flush();
		}).isInstanceOf(DataIntegrityViolationException.class);
	}
}
