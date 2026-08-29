package com.operion.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.finance.FeeService.InstallmentInput;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.student.Student;
import com.operion.student.StudentDocumentRepository;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentExitRepository;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
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
 * Proves StudentFeeAssignment's discount-approval gate and its mutable-pre-invoice /
 * superseded-post-invoice split, per ai-context/erp-system-plan.md §3.2.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, FeeService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentFeeAssignmentTest {

	private StudentService studentService;

	@Autowired
	private FeeService feeService;

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
	private StudentFeeAssignmentRepository studentFeeAssignmentRepository;

	@Autowired
	private FeeStructureInstallmentRepository feeStructureInstallmentRepository;

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

	private record Fixture(FeeStructure feeStructure, StudentEnrollment enrollment) {
	}

	private Fixture setUpFixture(String orgSlug, String admissionNumber) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", orgSlug));
		TenantContext.set(organisation.getId(), null);

		AcademicYear academicYear =
				academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(academicYear, campus, grade5, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		Person person = personRepository.save(new Person("Meera", "Nair"));

		Student student = studentService.admit(
				person, admissionNumber, LocalDate.of(2025, 5, 1), null, null, null, null, null, null, "Indian", null);
		StudentEnrollment enrollment = studentService.enroll(student, academicYear, section, 12, LocalDate.of(2025, 6, 1));

		FeeCategory feeCategory = feeService.createCategory("TUITION", "Tuition Fee", null);
		FeeStructure feeStructure = feeService.createFeeStructure(academicYear, schoolClass, feeCategory, new BigDecimal("10000.00"),
				List.of(new InstallmentInput(1, LocalDate.of(2025, 6, 15), new BigDecimal("10000.00"))));

		return new Fixture(feeStructure, enrollment);
	}

	@Test
	void discountRequiresAReasonAndAnApprover() {
		Fixture fixture = setUpFixture("fee-assignment-discount-school", "ADM-200");

		assertThatThrownBy(() -> feeService.assignFee(
				fixture.enrollment(), fixture.feeStructure(), new BigDecimal("1000.00"), null, null))
				.isInstanceOf(IllegalArgumentException.class);

		StudentFeeAssignment assignment = feeService.assignFee(
				fixture.enrollment(), fixture.feeStructure(), new BigDecimal("1000.00"), "Sibling discount", 42L);
		assertThat(assignment.getEffectiveAmount()).isEqualByComparingTo("9000.00");
	}

	@Test
	void revisionMutatesInPlacePreInvoiceButSupersedesOnceAnInvoiceExists() {
		Fixture fixture = setUpFixture("fee-assignment-revise-school", "ADM-201");
		StudentFeeAssignment assignment = feeService.assignFee(fixture.enrollment(), fixture.feeStructure(), null, null, null);

		StudentFeeAssignment revisedPreInvoice =
				feeService.reviseAssignment(assignment, new BigDecimal("500.00"), "Late enrollment adjustment", 7L);
		assertThat(revisedPreInvoice.getId()).isEqualTo(assignment.getId());
		assertThat(revisedPreInvoice.getEffectiveAmount()).isEqualByComparingTo("9500.00");
		assertThat(studentFeeAssignmentRepository.findByStudentEnrollmentId(fixture.enrollment().getId())).hasSize(1);

		FeeStructureInstallment installment =
				feeStructureInstallmentRepository.findByFeeStructureIdOrderByInstallmentNumber(fixture.feeStructure().getId()).get(0);
		feeService.generateInvoice(revisedPreInvoice, installment);

		StudentFeeAssignment revisedPostInvoice =
				feeService.reviseAssignment(revisedPreInvoice, new BigDecimal("1000.00"), "Additional discount approved", 7L);
		assertThat(revisedPostInvoice.getId()).isNotEqualTo(revisedPreInvoice.getId());
		assertThat(revisedPostInvoice.getEffectiveAmount()).isEqualByComparingTo("9000.00");

		List<StudentFeeAssignment> all = studentFeeAssignmentRepository.findByStudentEnrollmentId(fixture.enrollment().getId());
		assertThat(all).hasSize(2);
		assertThat(all).anySatisfy(a -> assertThat(a.getId()).isEqualTo(revisedPreInvoice.getId()))
				.filteredOn(a -> a.getId().equals(revisedPreInvoice.getId()))
				.allSatisfy(a -> assertThat(a.getStatus()).isEqualTo(StudentFeeAssignmentStatus.SUPERSEDED));
	}
}
