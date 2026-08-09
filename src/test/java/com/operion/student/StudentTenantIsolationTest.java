package com.operion.student;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
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

/**
 * Extends the tenant-isolation proof from OrganisationTenantIsolationTest to the
 * Student Management tables - closes the coverage gap Academic Foundation shipped with
 * (only its own service logic was tested, not @TenantId on its new tables).
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentTenantIsolationTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private StudentRepository studentRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void queriesOnlySeeTheCurrentTenantsStudents() {
		Organisation orgA = organisationRepository.save(new Organisation("A School", "A School Trust", "iso-a-school"));
		Organisation orgB = organisationRepository.save(new Organisation("B School", "B School Trust", "iso-b-school"));

		TenantContext.set(orgA.getId(), null);
		Person personA = personRepository.save(new Person("Aditi", "Kumar"));
		studentRepository.save(
				new Student(personA, "A-001", LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null));

		TenantContext.set(orgB.getId(), null);
		Person personB = personRepository.save(new Person("Baldev", "Singh"));
		studentRepository.save(
				new Student(personB, "B-001", LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null));

		TenantContext.set(orgA.getId(), null);
		List<Student> visibleToA = studentRepository.findAll();

		assertThat(visibleToA).hasSize(1);
		assertThat(visibleToA.get(0).getAdmissionNumber()).isEqualTo("A-001");
		assertThat(visibleToA.get(0).getOrganisationId()).isEqualTo(orgA.getId());
	}
}
