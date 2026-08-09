package com.operion.parent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.Student;
import com.operion.student.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves ParentService.linkGuardian enforces at most one primary guardian per student -
 * linking a second primary unsets the first, rather than allowing both rows to claim
 * is_primary_guardian, per ai-context/erp-system-plan.md §2.3.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, ParentService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentGuardianPrimaryTest {

	@Autowired
	private ParentService parentService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private StudentGuardianRepository studentGuardianRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void linkingASecondPrimaryGuardianUnsetsTheFirst() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "guardian-school"));
		TenantContext.set(organisation.getId(), null);

		Person studentPerson = personRepository.save(new Person("Ira", "Shah"));
		Student student = studentRepository.save(
				new Student(studentPerson, "ADM-010", LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null));

		Person fatherPerson = personRepository.save(new Person("Vikram", "Shah"));
		Person motherPerson = personRepository.save(new Person("Anita", "Shah"));
		Guardian father = parentService.getOrCreateGuardian(fatherPerson, "Engineer");
		Guardian mother = parentService.getOrCreateGuardian(motherPerson, "Doctor");

		StudentGuardian fatherLink = parentService.linkGuardian(
				student, father, GuardianRelationshipType.FATHER, true, true, true, true, 1);
		assertThat(fatherLink.isPrimaryGuardian()).isTrue();

		StudentGuardian motherLink = parentService.linkGuardian(
				student, mother, GuardianRelationshipType.MOTHER, true, true, true, true, 2);
		assertThat(motherLink.isPrimaryGuardian()).isTrue();

		StudentGuardian reloadedFatherLink = studentGuardianRepository.findById(fatherLink.getId()).orElseThrow();
		assertThat(reloadedFatherLink.isPrimaryGuardian()).isFalse();
		assertThat(studentGuardianRepository.findByStudentIdAndPrimaryGuardianTrue(student.getId()))
				.map(StudentGuardian::getId)
				.contains(motherLink.getId());
	}
}
