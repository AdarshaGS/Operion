package com.operion.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import com.operion.common.imports.ImportRunRepository;
import com.operion.common.imports.ImportRunService;
import com.operion.finance.FeeService;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Covers the shared validate-then-confirm shape for the combined Vehicle+Route bulk
 * import (Imports & exports rebuild): a vehicle-only row, a vehicle+route row, the two
 * duplicate rules (vehicle-only duplicate vs. vehicle-exists-but-new-route still
 * importing), and an invalid vehicleType enum - same per-row-transaction convention as
 * StudentImportServiceTest.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, FeeService.class, TransportService.class, VehicleRouteRowImportService.class,
		VehicleRouteImportService.class, ImportRunService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VehicleRouteImportServiceTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private RouteRepository routeRepository;

	@Autowired
	private VehicleRouteImportService vehicleRouteImportService;

	@Autowired
	private ImportRunRepository importRunRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private void newTenant(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);
		campusRepository.save(new Campus("Main Campus", "MAIN"));
	}

	private MockMultipartFile csv(String... lines) {
		return new MockMultipartFile("file", "vehicles.csv", "text/csv", String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
	}

	private static final String HEADER = "campus,registrationNumber,vehicleType,capacity,driverName,attendantName,routeName,routeCode";

	@Test
	void importsAVehicleOnlyRow() {
		newTenant("vehicle-import-happy");

		MockMultipartFile file = csv(HEADER, "Main Campus,KA-01-AB-1234,BUS,40,,,,");

		List<ImportRowResult> results = vehicleRouteImportService.importFile(file, false);

		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.IMPORTED);
		assertThat(vehicleRepository.findAll()).extracting(Vehicle::getRegistrationNumber).containsExactly("KA-01-AB-1234");
		assertThat(routeRepository.findAll()).isEmpty();
		assertThat(importRunRepository.findAllByOrderByCreatedAtDesc()).singleElement()
				.satisfies(run -> assertThat(run.getImportedCount()).isEqualTo(1));
	}

	@Test
	void importsAVehicleAndAttachedRouteInOneRow() {
		newTenant("vehicle-import-with-route");

		MockMultipartFile file = csv(HEADER, "Main Campus,KA-01-AB-1234,BUS,40,,,Route 1,R1");

		List<ImportRowResult> results = vehicleRouteImportService.importFile(file, false);

		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.IMPORTED);
		assertThat(vehicleRepository.findAll()).hasSize(1);
		assertThat(routeRepository.findAll()).extracting(Route::getCode).containsExactly("R1");
	}

	@Test
	void duplicateRegistrationNumberWithNoRouteColumnsIsReportedAndCreatesNothing() {
		newTenant("vehicle-import-duplicate");
		Campus campus = campusRepository.findAll().get(0);
		vehicleRepository.save(new Vehicle(campus, "KA-01-AB-1234", VehicleType.BUS, 40, null, null));

		MockMultipartFile file = csv(HEADER, "Main Campus,KA-01-AB-1234,BUS,40,,,,");

		List<ImportRowResult> results = vehicleRouteImportService.importFile(file, false);

		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.DUPLICATE);
		assertThat(vehicleRepository.findAll()).hasSize(1);
	}

	/** Judgement call: an existing vehicle plus a new route on the same row still counts
	 * as IMPORTED (something new happened - the route), rather than DUPLICATE, and the
	 * existing vehicle is reused rather than recreated. */
	@Test
	void existingVehicleWithANewRouteStillImportsInsteadOfHardDuplicating() {
		newTenant("vehicle-import-existing-vehicle-new-route");
		Campus campus = campusRepository.findAll().get(0);
		Vehicle existing = vehicleRepository.save(new Vehicle(campus, "KA-01-AB-1234", VehicleType.BUS, 40, null, null));

		MockMultipartFile file = csv(HEADER, "Main Campus,KA-01-AB-1234,BUS,40,,,Route 1,R1");

		List<ImportRowResult> results = vehicleRouteImportService.importFile(file, false);

		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.IMPORTED);
		assertThat(results.get(0).id()).isEqualTo(existing.getId());
		assertThat(vehicleRepository.findAll()).hasSize(1);
		assertThat(routeRepository.findAll()).extracting(Route::getCode).containsExactly("R1");
	}

	@Test
	void invalidVehicleTypeIsReportedAsAnError() {
		newTenant("vehicle-import-invalid-enum");

		MockMultipartFile file = csv(HEADER, "Main Campus,KA-01-AB-1234,HELICOPTER,40,,,,");

		List<ImportRowResult> results = vehicleRouteImportService.importFile(file, false);

		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.ERROR);
		assertThat(vehicleRepository.findAll()).isEmpty();
	}

	@Test
	void validateOnlyPersistsNothing() {
		newTenant("vehicle-import-validate-only");

		MockMultipartFile file = csv(HEADER, "Main Campus,KA-01-AB-1234,BUS,40,,,Route 1,R1");

		List<ImportRowResult> results = vehicleRouteImportService.importFile(file, true);

		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.VALID);
		assertThat(vehicleRepository.findAll()).isEmpty();
		assertThat(routeRepository.findAll()).isEmpty();
		assertThat(importRunRepository.findAll()).isEmpty();
	}
}
