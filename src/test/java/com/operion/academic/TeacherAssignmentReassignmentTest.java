package com.operion.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
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
 * Proves the insert-only teacher-reassignment logic in AcademicService.assignTeacher:
 * the previous ACTIVE row for a (section, subject, type) slot is ended, never mutated
 * in place, per ai-context/erp-system-plan.md §2.1.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, AcademicService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TeacherAssignmentReassignmentTest {

	@Autowired
	private AcademicService academicService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private AcademicYearRepository academicYearRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private GradeLevelRepository gradeLevelRepository;

	@Autowired
	private SchoolClassRepository schoolClassRepository;

	@Autowired
	private SectionRepository sectionRepository;

	@Autowired
	private TeacherAssignmentRepository teacherAssignmentRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void reassigningATeacherEndsThePreviousRowAndStartsANewOne() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "test-school"));
		TenantContext.set(organisation.getId(), null);

		AcademicYear academicYear = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel gradeLevel = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(academicYear, campus, gradeLevel, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		Person firstTeacher = personRepository.save(new Person("Asha", "Rao"));
		Person secondTeacher = personRepository.save(new Person("Bala", "Iyer"));

		TeacherAssignment first = academicService.assignTeacher(
				section, null, firstTeacher, TeacherAssignmentType.HOMEROOM, LocalDate.of(2025, 6, 1));
		TeacherAssignment second = academicService.assignTeacher(
				section, null, secondTeacher, TeacherAssignmentType.HOMEROOM, LocalDate.of(2025, 12, 1));

		TeacherAssignment reloadedFirst = teacherAssignmentRepository.findById(first.getId()).orElseThrow();
		assertThat(reloadedFirst.getStatus()).isEqualTo(TeacherAssignmentStatus.ENDED);
		assertThat(reloadedFirst.getEndDate()).isEqualTo(LocalDate.of(2025, 12, 1));
		assertThat(reloadedFirst.getTeacherPerson().getId()).isEqualTo(firstTeacher.getId());

		assertThat(second.getStatus()).isEqualTo(TeacherAssignmentStatus.ACTIVE);
		assertThat(teacherAssignmentRepository.findBySectionIdAndAssignmentTypeAndStatus(
				section.getId(), TeacherAssignmentType.HOMEROOM, TeacherAssignmentStatus.ACTIVE))
				.map(TeacherAssignment::getId)
				.contains(second.getId());
	}
}
