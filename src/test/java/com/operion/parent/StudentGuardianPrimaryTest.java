package com.operion.parent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataIntegrityViolationException;
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
				new Student(studentPerson, "STU-ADM-010", "ADM-010", LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null, null, null, null, null));

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
	private Student newStudent(String admissionNumber) {
		Person person = personRepository.save(new Person("Child" + admissionNumber, "Shah"));
		return studentRepository.save(
				new Student(person, "STU-" + admissionNumber, admissionNumber, LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null, null, null, null, null));
	}

	private Guardian newGuardian(String firstName) {
		return parentService.getOrCreateGuardian(personRepository.save(new Person(firstName, "Shah")), null);
	}

	@Test
	void updatingAnotherGuardianToPrimaryDemotesTheCurrentPrimary() {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", "guardian-update-school"));
		TenantContext.set(organisation.getId(), null);
		Student student = newStudent("ADM-011");
		StudentGuardian father = parentService.linkGuardian(student, newGuardian("Vikram"), GuardianRelationshipType.FATHER, true, true, true, true, 1);
		StudentGuardian mother = parentService.linkGuardian(student, newGuardian("Anita"), GuardianRelationshipType.MOTHER, false, true, true, true, 2);
		assertThat(mother.getPrimaryMarker()).isNull();

		parentService.updateRelationship(mother, null, true, null, null, null, null);

		StudentGuardian reloadedFather = studentGuardianRepository.findById(father.getId()).orElseThrow();
		StudentGuardian reloadedMother = studentGuardianRepository.findById(mother.getId()).orElseThrow();
		assertThat(reloadedFather.isPrimaryGuardian()).isFalse();
		assertThat(reloadedFather.getPrimaryMarker()).isNull();
		assertThat(reloadedMother.isPrimaryGuardian()).isTrue();
		assertThat(reloadedMother.getPrimaryMarker()).isTrue();
		assertThat(studentGuardianRepository.findByStudentIdAndPrimaryGuardianTrue(student.getId())).map(StudentGuardian::getId).contains(mother.getId());
	}

	@Test
	void switchingThePrimaryBackAndForthIsAlwaysAccepted() {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", "guardian-switch-school"));
		TenantContext.set(organisation.getId(), null);
		Student student = newStudent("ADM-012");
		StudentGuardian father = parentService.linkGuardian(student, newGuardian("Vikram"), GuardianRelationshipType.FATHER, true, true, true, true, 1);
		StudentGuardian mother = parentService.linkGuardian(student, newGuardian("Anita"), GuardianRelationshipType.MOTHER, false, true, true, true, 2);

		parentService.updateRelationship(mother, null, true, null, null, null, null);
		parentService.updateRelationship(studentGuardianRepository.findById(father.getId()).orElseThrow(), null, true, null, null, null, null);
		parentService.updateRelationship(studentGuardianRepository.findById(mother.getId()).orElseThrow(), null, true, null, null, null, null);

		assertThat(studentGuardianRepository.findAll()).filteredOn(StudentGuardian::isPrimaryGuardian).extracting(StudentGuardian::getId)
				.containsExactly(mother.getId());
	}

	@Test
	void deactivatingThePrimaryClearsTheFlagAndFreesTheSlot() {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", "guardian-deactivate-school"));
		TenantContext.set(organisation.getId(), null);
		Student student = newStudent("ADM-013");
		StudentGuardian father = parentService.linkGuardian(student, newGuardian("Vikram"), GuardianRelationshipType.FATHER, true, true, true, true, 1);

		father.deactivate();
		studentGuardianRepository.saveAndFlush(father);

		StudentGuardian reloaded = studentGuardianRepository.findById(father.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(StudentGuardianStatus.INACTIVE);
		assertThat(reloaded.isPrimaryGuardian()).isFalse();
		assertThat(reloaded.getPrimaryMarker()).isNull();
		assertThat(studentGuardianRepository.findByStudentIdAndPrimaryGuardianTrue(student.getId())).isEmpty();

		// The slot is free: a new primary can be linked and is the only one.
		StudentGuardian mother = parentService.linkGuardian(student, newGuardian("Anita"), GuardianRelationshipType.MOTHER, true, true, true, true, 2);
		assertThat(studentGuardianRepository.findByStudentIdAndPrimaryGuardianTrue(student.getId())).map(StudentGuardian::getId).contains(mother.getId());
	}

	@Test
	void theDatabaseRefusesASecondActivePrimaryForTheSameStudent() {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", "guardian-unique-school"));
		TenantContext.set(organisation.getId(), null);
		Student student = newStudent("ADM-014");
		parentService.linkGuardian(student, newGuardian("Vikram"), GuardianRelationshipType.FATHER, true, true, true, true, 1);
		Guardian mother = newGuardian("Anita");

		// Straight through the repository, bypassing ParentService's demotion of the old primary.
		assertThatThrownBy(() -> studentGuardianRepository.saveAndFlush(
				new StudentGuardian(student, mother, GuardianRelationshipType.MOTHER, true, true, true, true, 2)))
				.isInstanceOf(DataIntegrityViolationException.class);
		// Several non-primary guardians per student are fine.
		studentGuardianRepository.saveAndFlush(new StudentGuardian(student, mother, GuardianRelationshipType.MOTHER, false, true, true, true, 2));
		studentGuardianRepository.saveAndFlush(new StudentGuardian(student, newGuardian("Uncle"), GuardianRelationshipType.OTHER, false, true, true, true, 3));
		assertThat(studentGuardianRepository.findByStudentId(student.getId())).hasSize(3);
	}
}
