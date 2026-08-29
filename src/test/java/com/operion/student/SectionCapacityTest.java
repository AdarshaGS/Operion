package com.operion.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves StudentService's section-capacity guard (enroll/promote/reassignSection all
 * reject once a section's current-enrollment count would exceed Section.capacity) -
 * previously claimed by Section's own Javadoc but never actually implemented.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SectionCapacityTest {

	private StudentService studentService;

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
	private StudentRepository studentRepository;

	@Autowired
	private StudentEnrollmentRepository studentEnrollmentRepository;

	@Autowired
	private StudentDocumentRepository studentDocumentRepository;

	@Autowired
	private StudentExitRepository studentExitRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@BeforeEach
	void setUpStudentService() {
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
				studentExitRepository, null, null, new AuditLogService(auditLogRepository, new ObjectMapper()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private Student admitAndSetupOrg(Organisation organisation, String admissionNumber, String firstName) {
		TenantContext.set(organisation.getId(), null);
		Person person = personRepository.save(new Person(firstName, "Test"));
		return studentService.admit(
				person, admissionNumber, LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null);
	}

	@Test
	void enrollRejectsOnceSectionCapacityIsReached() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "capacity-enroll-school"));
		TenantContext.set(organisation.getId(), null);

		AcademicYear academicYear = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(academicYear, campus, grade5, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 1, null));

		Student first = admitAndSetupOrg(organisation, "ADM-CAP-1", "First");
		studentService.enroll(first, academicYear, section, 1, LocalDate.of(2025, 6, 1));

		Student second = admitAndSetupOrg(organisation, "ADM-CAP-2", "Second");
		assertThatThrownBy(() -> studentService.enroll(second, academicYear, section, 2, LocalDate.of(2025, 6, 1)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("capacity");
	}

	@Test
	void enrollAllowsUnlimitedStudentsWhenCapacityIsNull() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "capacity-null-school"));
		TenantContext.set(organisation.getId(), null);

		AcademicYear academicYear = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(academicYear, campus, grade5, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", null, null));

		Student first = admitAndSetupOrg(organisation, "ADM-CAP-3", "First");
		Student second = admitAndSetupOrg(organisation, "ADM-CAP-4", "Second");

		studentService.enroll(first, academicYear, section, 1, LocalDate.of(2025, 6, 1));
		StudentEnrollment secondEnrollment =
				studentService.enroll(second, academicYear, section, 2, LocalDate.of(2025, 6, 1));

		assertThat(secondEnrollment.isCurrent()).isTrue();
	}

	@Test
	void promoteRejectsWhenTargetSectionIsFull() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "capacity-promote-school"));
		TenantContext.set(organisation.getId(), null);

		AcademicYear ay1 = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		AcademicYear ay2 = academicYearRepository.save(
				new AcademicYear("2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		GradeLevel grade6 = gradeLevelRepository.save(new GradeLevel("Grade 6", 6, null));
		SchoolClass class5 = schoolClassRepository.save(new SchoolClass(ay1, campus, grade5, null));
		SchoolClass class6 = schoolClassRepository.save(new SchoolClass(ay2, campus, grade6, null));
		Section section5A = sectionRepository.save(new Section(class5, "A", 40, null));
		Section section6A = sectionRepository.save(new Section(class6, "A", 1, null));

		Student staying = admitAndSetupOrg(organisation, "ADM-CAP-5", "Staying");
		studentService.enroll(staying, ay1, section6A, 1, LocalDate.of(2025, 6, 1));

		Student promoting = admitAndSetupOrg(organisation, "ADM-CAP-6", "Promoting");
		studentService.enroll(promoting, ay1, section5A, 2, LocalDate.of(2025, 6, 1));

		assertThatThrownBy(
				() -> studentService.promote(promoting, ay2, section6A, 2, LocalDate.of(2026, 4, 30), false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("capacity");
	}

	@Test
	void reassignSectionRejectsWhenTargetIsFullButAllowsReassigningToTheSameSection() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "capacity-reassign-school"));
		TenantContext.set(organisation.getId(), null);

		AcademicYear academicYear = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(academicYear, campus, grade5, null));
		Section sectionA = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		Section sectionB = sectionRepository.save(new Section(schoolClass, "B", 1, null));

		Student inSectionB = admitAndSetupOrg(organisation, "ADM-CAP-7", "InB");
		studentService.enroll(inSectionB, academicYear, sectionB, 1, LocalDate.of(2025, 6, 1));

		Student inSectionA = admitAndSetupOrg(organisation, "ADM-CAP-8", "InA");
		studentService.enroll(inSectionA, academicYear, sectionA, 2, LocalDate.of(2025, 6, 1));

		assertThatThrownBy(() -> studentService.reassignSection(inSectionA, sectionB))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("capacity");

		// Reassigning a student already in the (full) section back to that same section is a no-op, not a capacity breach.
		StudentEnrollment reassigned = studentService.reassignSection(inSectionB, sectionB);
		assertThat(reassigned.getSection().getId()).isEqualTo(sectionB.getId());
	}
}
