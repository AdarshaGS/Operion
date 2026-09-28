package com.operion.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.communication.CommunicationService;
import com.operion.finance.FeeService;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Proves DriverProfile upserts in place - a renewal updates the existing row rather
 * than inserting a second one, same "one row, update in place" convention as
 * StaffBankDetail. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, TransportService.class, FeeService.class, TransportNotificationService.class,
		CommunicationService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DriverProfileTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private DriverProfileRepository driverProfileRepository;

	@Autowired
	private TransportService transportService;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private Person setUpPerson(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);
		return personRepository.save(new Person("Ramesh", "Kumar"));
	}

	@Test
	void upsertingCreatesAProfileWhenNoneExists() {
		Person driver = setUpPerson("driver-profile-create");

		DriverProfile profile = transportService.upsertDriverProfile(driver, "DL-001", LocalDate.of(2028, 5, 1));

		assertThat(profile.getLicenceNumber()).isEqualTo("DL-001");
		assertThat(profile.getLicenceExpiryDate()).isEqualTo(LocalDate.of(2028, 5, 1));
	}

	@Test
	void upsertingTwiceUpdatesTheSameRowRatherThanInsertingASecondOne() {
		Person driver = setUpPerson("driver-profile-renew");
		DriverProfile first = transportService.upsertDriverProfile(driver, "DL-001", LocalDate.of(2026, 5, 1));

		DriverProfile renewed = transportService.upsertDriverProfile(driver, "DL-002", LocalDate.of(2031, 5, 1));

		assertThat(renewed.getId()).isEqualTo(first.getId());
		assertThat(renewed.getLicenceNumber()).isEqualTo("DL-002");
		assertThat(driverProfileRepository.findByPersonId(driver.getId())).isPresent();
	}
}
