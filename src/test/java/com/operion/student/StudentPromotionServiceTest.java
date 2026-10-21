package com.operion.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.customfield.CustomFieldDefinitionRepository;
import com.operion.customfield.CustomFieldService;
import com.operion.common.InvalidRequestException;
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

@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, StudentIdGenerator.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentPromotionServiceTest {

	private StudentService studentService;
	private StudentPromotionService promotionService;

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
	private StudentIdGenerator studentIdGenerator;
	@Autowired
	private AuditLogRepository auditLogRepository;
	@Autowired
	private CustomFieldDefinitionRepository customFieldDefinitionRepository;

	@BeforeEach
	void setUp() {
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
			studentExitRepository, null, null, studentIdGenerator, new AuditLogService(auditLogRepository, new ObjectMapper()),
			new CustomFieldService(customFieldDefinitionRepository, new ObjectMapper()));
		promotionService = new StudentPromotionService(studentService, studentRepository, academicYearRepository, sectionRepository);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void appliesEachDecisionIndependentlyAndReportsFailuresWithoutBlockingTheRest() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "bulk-promotion-school"));
		TenantContext.set(organisation.getId(), null);
		AcademicYear ay1 = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		AcademicYear ay2 = academicYearRepository.save(
				new AcademicYear("2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		GradeLevel grade6 = gradeLevelRepository.save(new GradeLevel("Grade 6", 6, null));
		Section section5A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay1, campus, grade5, null)), "A", 40, null));
		Section nextYear5A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay2, campus, grade5, null)), "A", 40, null));
		Section nextYear6A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay2, campus, grade6, null)), "A", 40, null));

		Student promoted = admitAndEnroll("Meera", "ADM-101", ay1, section5A, 1);
		Student repeater = admitAndEnroll("Rohan", "ADM-102", ay1, section5A, 2);
		Student graduate = admitAndEnroll("Asha", "ADM-103", ay1, section5A, 3);
		Student missingSection = admitAndEnroll("Kiran", "ADM-104", ay1, section5A, 4);

		List<StudentPromotionService.Outcome> outcomes = promotionService.apply(ay2.getId(), LocalDate.of(2026, 4, 30), List.of(
				new StudentPromotionService.Decision(promoted.getId(), StudentPromotionService.Action.PROMOTE, nextYear6A.getId(), 1),
				new StudentPromotionService.Decision(repeater.getId(), StudentPromotionService.Action.REPEAT, nextYear5A.getId(), 1),
				new StudentPromotionService.Decision(graduate.getId(), StudentPromotionService.Action.GRADUATE, null, null),
				new StudentPromotionService.Decision(missingSection.getId(), StudentPromotionService.Action.PROMOTE, null, null)), null);

		assertThat(outcomes).extracting(StudentPromotionService.Outcome::succeeded).containsExactly(true, true, true, false);
		assertThat(outcomes.get(3).error()).contains("target section");

		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(promoted.getId()))
				.map(e -> e.getSection().getId()).contains(nextYear6A.getId());
		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(repeater.getId()))
				.map(e -> e.getSection().getId()).contains(nextYear5A.getId());
		assertThat(studentEnrollmentRepository.findByStudentId(repeater.getId()))
				.extracting(StudentEnrollment::getEnrollmentStatus)
				.contains(StudentEnrollmentStatus.REPEATED);
		assertThat(studentRepository.findById(graduate.getId()).orElseThrow().getStatus()).isEqualTo(StudentStatus.GRADUATED);
		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(graduate.getId())).isEmpty();
		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(missingSection.getId()))
				.map(e -> e.getSection().getId()).contains(section5A.getId());
	}

	@Test
	void promotingOrRepeatingRequiresATargetYear() {
		assertThatThrownBy(() -> promotionService.apply(null, LocalDate.of(2026, 4, 30), List.of(
				new StudentPromotionService.Decision(1L, StudentPromotionService.Action.PROMOTE, 1L, null)), null))
				.isInstanceOf(InvalidRequestException.class);
	}

	private Student admitAndEnroll(String firstName, String admissionNumber, AcademicYear year, Section section, int roll) {
		Person person = personRepository.save(new Person(firstName, "Test"));
		Student student = studentService.admit(
				person, admissionNumber, LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null, null, null, null);
		studentService.enroll(student, year, section, roll, LocalDate.of(2025, 6, 1));
		return student;
	}
}
