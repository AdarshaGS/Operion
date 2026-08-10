package com.operion.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
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
 * Extends the standing tenant-isolation proof (OrganisationTenantIsolationTest /
 * StudentTenantIsolationTest / AttendanceTenantIsolationTest / FeeTenantIsolationTest /
 * ExaminationTenantIsolationTest / CommunicationTenantIsolationTest /
 * TransportTenantIsolationTest / LibraryTenantIsolationTest /
 * InventoryTenantIsolationTest) to the HR tables.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, HrService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HrTenantIsolationTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private HrService hrService;

	@Autowired
	private StaffProfileRepository staffProfileRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void queriesOnlySeeTheCurrentTenantsStaffProfiles() {
		Organisation orgA = organisationRepository.save(new Organisation("A School", "A School Trust", "hr-iso-a-school"));
		TenantContext.set(orgA.getId(), null);
		Campus campusA = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Person personA = personRepository.save(new Person("Org A", "Staff"));
		hrService.createStaffProfile(personA, campusA, "EMP-A-1", "Teacher", null, LocalDate.of(2021, 1, 1), EmploymentType.PERMANENT);

		Organisation orgB = organisationRepository.save(new Organisation("B School", "B School Trust", "hr-iso-b-school"));
		TenantContext.set(orgB.getId(), null);
		Campus campusB = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Person personB = personRepository.save(new Person("Org B", "Staff"));
		hrService.createStaffProfile(personB, campusB, "EMP-B-1", "Teacher", null, LocalDate.of(2021, 1, 1), EmploymentType.PERMANENT);

		TenantContext.set(orgA.getId(), null);
		List<StaffProfile> visibleToA = staffProfileRepository.findAll();

		assertThat(visibleToA).hasSize(1);
		assertThat(visibleToA.get(0).getOrganisationId()).isEqualTo(orgA.getId());
		assertThat(visibleToA.get(0).getEmployeeCode()).isEqualTo("EMP-A-1");
	}
}
