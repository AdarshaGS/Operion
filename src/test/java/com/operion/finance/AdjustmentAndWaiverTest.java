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
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.finance.FeeService.AllocationInput;
import com.operion.finance.FeeService.InstallmentInput;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.Student;
import com.operion.student.StudentDocumentRepository;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentExitRepository;
import com.operion.student.StudentIdGenerator;
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
 * Proves Adjustment/Waiver (#131) are additive, insert-only corrections to an Invoice's
 * totalAmount - an adjustment can raise or lower what's owed but never below what's
 * already been paid, and a waiver forgives at most the current outstanding balance.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, FeeService.class, StudentIdGenerator.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdjustmentAndWaiverTest {

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
	private FeeStructureInstallmentRepository feeStructureInstallmentRepository;

	@Autowired
	private InvoiceRepository invoiceRepository;

	@Autowired
	private AdjustmentRepository adjustmentRepository;

	@Autowired
	private WaiverRepository waiverRepository;

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

	@BeforeEach
	void setUpStudentService() {
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
			studentExitRepository, null, null, studentIdGenerator, new AuditLogService(auditLogRepository, new ObjectMapper()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(AcademicYear academicYear, Invoice invoice) {
	}

	/** A single 9000.00 invoice (10000 structure, 1000 discount, one installment covering the whole structure amount). */
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
				person, admissionNumber, LocalDate.of(2025, 5, 1), null, null, null, null, null, null, "Indian", null, null, null, null);
		StudentEnrollment enrollment = studentService.enroll(student, academicYear, section, 12, LocalDate.of(2025, 6, 1));

		FeeStructureGroup feeStructureGroup = feeService.createFeeStructureGroup("Grade 5 Annual Fees 2025-26", academicYear, schoolClass);
		FeeCategory feeCategory = feeService.createCategory("TUITION", "Tuition Fee", null);
		FeeStructure feeStructure = feeService.createFeeStructure(feeStructureGroup, feeCategory, new BigDecimal("10000.00"),
				List.of(new InstallmentInput(1, LocalDate.of(2025, 6, 15), new BigDecimal("10000.00"))));

		StudentFeeAssignment assignment =
				feeService.assignFee(enrollment, feeStructure, new BigDecimal("1000.00"), "Sibling discount", 42L);
		FeeStructureInstallment installment =
				feeStructureInstallmentRepository.findByFeeStructureIdOrderByInstallmentNumber(feeStructure.getId()).get(0);

		Invoice invoice = feeService.generateInvoice(assignment, installment);
		return new Fixture(academicYear, invoice);
	}

	@Test
	void adjustmentCanIncreaseOrDecreaseWhatsOwedAdditively() {
		Fixture fixture = setUpFixture("fee-adjustment-increase-school", "ADM-350");

		Adjustment increase = feeService.recordAdjustment(fixture.invoice(), new BigDecimal("500.00"), "Billing correction - missed lab fee", 7L, LocalDate.of(2025, 6, 20));
		assertThat(increase.getAmount()).isEqualByComparingTo("500.00");

		Invoice afterIncrease = invoiceRepository.findById(fixture.invoice().getId()).orElseThrow();
		assertThat(afterIncrease.getTotalAmount()).isEqualByComparingTo("9500.00");
		assertThat(afterIncrease.getStatus()).isEqualTo(InvoiceStatus.ISSUED);

		feeService.recordAdjustment(afterIncrease, new BigDecimal("-200.00"), "Correction reversed partially", 7L, LocalDate.of(2025, 6, 21));
		Invoice afterDecrease = invoiceRepository.findById(fixture.invoice().getId()).orElseThrow();
		assertThat(afterDecrease.getTotalAmount()).isEqualByComparingTo("9300.00");
		assertThat(adjustmentRepository.findByInvoiceId(fixture.invoice().getId())).hasSize(2);
	}

	@Test
	void adjustmentRejectsReducingTotalBelowWhatsAlreadyBeenPaid() {
		Fixture fixture = setUpFixture("fee-adjustment-reject-school", "ADM-351");
		feeService.recordPayment(fixture.academicYear(), new BigDecimal("9000.00"), PaymentMethod.CASH, LocalDate.of(2025, 6, 20), null,
				List.of(new AllocationInput(fixture.invoice().getId(), new BigDecimal("9000.00"))));
		Invoice paidInvoice = invoiceRepository.findById(fixture.invoice().getId()).orElseThrow();

		assertThatThrownBy(() -> feeService.recordAdjustment(paidInvoice, new BigDecimal("-500.00"), "Too large a correction", 7L, LocalDate.of(2025, 6, 22)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void waiverForgivesOutstandingBalanceAndRejectsExceedingIt() {
		Fixture fixture = setUpFixture("fee-waiver-school", "ADM-352");
		feeService.recordPayment(fixture.academicYear(), new BigDecimal("2000.00"), PaymentMethod.CASH, LocalDate.of(2025, 6, 20), null,
				List.of(new AllocationInput(fixture.invoice().getId(), new BigDecimal("2000.00"))));
		Invoice partiallyPaidInvoice = invoiceRepository.findById(fixture.invoice().getId()).orElseThrow();
		assertThat(partiallyPaidInvoice.getOutstanding()).isEqualByComparingTo("7000.00");

		assertThatThrownBy(() -> feeService.recordWaiver(partiallyPaidInvoice, new BigDecimal("8000.00"), "Hardship waiver", 7L, LocalDate.of(2025, 6, 23)))
				.isInstanceOf(IllegalArgumentException.class);

		Waiver waiver = feeService.recordWaiver(partiallyPaidInvoice, new BigDecimal("7000.00"), "Hardship waiver", 7L, LocalDate.of(2025, 6, 23));
		assertThat(waiver.getAmount()).isEqualByComparingTo("7000.00");

		Invoice fullyResolvedInvoice = invoiceRepository.findById(fixture.invoice().getId()).orElseThrow();
		assertThat(fullyResolvedInvoice.getOutstanding()).isEqualByComparingTo("0.00");
		assertThat(fullyResolvedInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
		assertThat(waiverRepository.findByInvoiceId(fixture.invoice().getId())).hasSize(1);
	}
}
