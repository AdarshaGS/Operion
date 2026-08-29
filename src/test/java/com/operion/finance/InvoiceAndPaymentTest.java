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
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
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
 * Proves the invoice/payment ledger: proportional discount at generation time, duplicate
 * generation rejection, multi-invoice allocation with sum validation, and that a
 * bounce/refund reverses Invoice.amountPaid additively without deleting/editing the
 * original Payment or PaymentAllocation rows. Per ai-context/erp-system-plan.md §3.2.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, FeeService.class, StudentIdGenerator.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InvoiceAndPaymentTest {

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
	private PaymentAllocationRepository paymentAllocationRepository;

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
				studentExitRepository, studentIdGenerator, new AuditLogService(auditLogRepository, new ObjectMapper()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(AcademicYear academicYear, StudentFeeAssignment assignment, List<FeeStructureInstallment> installments) {
	}

	/** Two installments of 5000 each on a 10000 structure, discounted to an effective 9000 (90%). */
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

		FeeCategory feeCategory = feeService.createCategory("TUITION", "Tuition Fee", null);
		FeeStructure feeStructure = feeService.createFeeStructure(academicYear, schoolClass, feeCategory, new BigDecimal("10000.00"),
				List.of(new InstallmentInput(1, LocalDate.of(2025, 6, 15), new BigDecimal("5000.00")),
						new InstallmentInput(2, LocalDate.of(2025, 10, 15), new BigDecimal("5000.00"))));

		StudentFeeAssignment assignment =
				feeService.assignFee(enrollment, feeStructure, new BigDecimal("1000.00"), "Sibling discount", 42L);
		List<FeeStructureInstallment> installments =
				feeStructureInstallmentRepository.findByFeeStructureIdOrderByInstallmentNumber(feeStructure.getId());

		return new Fixture(academicYear, assignment, installments);
	}

	@Test
	void generatesProportionallyDiscountedInvoicesAndRejectsDuplicateGeneration() {
		Fixture fixture = setUpFixture("fee-invoice-school", "ADM-300");
		FeeStructureInstallment firstInstallment = fixture.installments().get(0);

		Invoice invoice = feeService.generateInvoice(fixture.assignment(), firstInstallment);

		assertThat(invoice.getTotalAmount()).isEqualByComparingTo("4500.00");
		assertThat(invoice.getInvoiceNumber()).isEqualTo("INV-2025-2026-000001");
		assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);

		assertThatThrownBy(() -> feeService.generateInvoice(fixture.assignment(), firstInstallment))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void allocatesOnePaymentAcrossMultipleInvoicesAndRejectsMismatchedAllocationSum() {
		Fixture fixture = setUpFixture("fee-payment-school", "ADM-301");
		Invoice invoice1 = feeService.generateInvoice(fixture.assignment(), fixture.installments().get(0));
		Invoice invoice2 = feeService.generateInvoice(fixture.assignment(), fixture.installments().get(1));

		assertThatThrownBy(() -> feeService.recordPayment(fixture.academicYear(), new BigDecimal("9000.00"), PaymentMethod.CASH,
				LocalDate.of(2025, 6, 20), null, List.of(new AllocationInput(invoice1.getId(), new BigDecimal("4000.00")))))
				.isInstanceOf(IllegalArgumentException.class);

		Payment payment = feeService.recordPayment(fixture.academicYear(), new BigDecimal("9000.00"), PaymentMethod.CASH,
				LocalDate.of(2025, 6, 20), "Full term settlement",
				List.of(new AllocationInput(invoice1.getId(), new BigDecimal("4500.00")),
						new AllocationInput(invoice2.getId(), new BigDecimal("4500.00"))));

		assertThat(payment.getReceiptNumber()).isEqualTo("RCT-2025-2026-000001");
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CLEARED);

		Invoice reloadedInvoice1 = invoiceRepository.findById(invoice1.getId()).orElseThrow();
		Invoice reloadedInvoice2 = invoiceRepository.findById(invoice2.getId()).orElseThrow();
		assertThat(reloadedInvoice1.getStatus()).isEqualTo(InvoiceStatus.PAID);
		assertThat(reloadedInvoice1.getOutstanding()).isEqualByComparingTo("0.00");
		assertThat(reloadedInvoice2.getStatus()).isEqualTo(InvoiceStatus.PAID);
	}

	@Test
	void bouncingAPaymentReversesAmountPaidWithoutDeletingTheAllocation() {
		Fixture fixture = setUpFixture("fee-bounce-school", "ADM-302");
		Invoice invoice = feeService.generateInvoice(fixture.assignment(), fixture.installments().get(0));
		Payment payment = feeService.recordPayment(fixture.academicYear(), new BigDecimal("4500.00"), PaymentMethod.CHEQUE,
				LocalDate.of(2025, 6, 20), null, List.of(new AllocationInput(invoice.getId(), new BigDecimal("4500.00"))));

		Payment bounced = feeService.bouncePayment(payment);
		assertThat(bounced.getStatus()).isEqualTo(PaymentStatus.BOUNCED);

		Invoice reloadedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
		assertThat(reloadedInvoice.getAmountPaid()).isEqualByComparingTo("0.00");
		assertThat(reloadedInvoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
		assertThat(paymentAllocationRepository.findByPaymentId(payment.getId())).hasSize(1);

		assertThatThrownBy(() -> feeService.bouncePayment(bounced)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void refundReducesAmountPaidAsAnAdditiveReversal() {
		Fixture fixture = setUpFixture("fee-refund-school", "ADM-303");
		Invoice invoice = feeService.generateInvoice(fixture.assignment(), fixture.installments().get(0));
		Payment payment = feeService.recordPayment(fixture.academicYear(), new BigDecimal("4500.00"), PaymentMethod.UPI,
				LocalDate.of(2025, 6, 20), null, List.of(new AllocationInput(invoice.getId(), new BigDecimal("4500.00"))));

		Invoice paidInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
		Refund refund = feeService.recordRefund(payment, paidInvoice, new BigDecimal("1500.00"), "Overpayment", 9L, LocalDate.of(2025, 6, 25));

		assertThat(refund.getAmount()).isEqualByComparingTo("1500.00");
		Invoice reloadedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
		assertThat(reloadedInvoice.getAmountPaid()).isEqualByComparingTo("3000.00");
		assertThat(reloadedInvoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
	}
}
