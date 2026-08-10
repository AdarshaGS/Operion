package com.operion.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Extends the standing tenant-isolation proof (OrganisationTenantIsolationTest /
 * StudentTenantIsolationTest / AttendanceTenantIsolationTest / FeeTenantIsolationTest /
 * ExaminationTenantIsolationTest / CommunicationTenantIsolationTest) to the
 * Transportation tables.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransportTenantIsolationTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void queriesOnlySeeTheCurrentTenantsVehicles() {
		Organisation orgA = organisationRepository.save(new Organisation("A School", "A School Trust", "transport-iso-a-school"));
		TenantContext.set(orgA.getId(), null);
		Campus campusA = campusRepository.save(new Campus("Main Campus", "MAIN"));
		vehicleRepository.save(new Vehicle(campusA, "KA-01-1111", VehicleType.BUS, 40, null, null));

		Organisation orgB = organisationRepository.save(new Organisation("B School", "B School Trust", "transport-iso-b-school"));
		TenantContext.set(orgB.getId(), null);
		Campus campusB = campusRepository.save(new Campus("Main Campus", "MAIN"));
		vehicleRepository.save(new Vehicle(campusB, "KA-01-2222", VehicleType.BUS, 40, null, null));

		TenantContext.set(orgA.getId(), null);
		List<Vehicle> visibleToA = vehicleRepository.findAll();

		assertThat(visibleToA).hasSize(1);
		assertThat(visibleToA.get(0).getOrganisationId()).isEqualTo(orgA.getId());
		assertThat(visibleToA.get(0).getRegistrationNumber()).isEqualTo("KA-01-1111");
	}
}
