package com.operion.finance;

import com.operion.common.InvalidRequestException;
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
import com.operion.finance.api.FeeOverviewController;
import com.operion.finance.api.FeeOverviewResponse;
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
import com.operion.customfield.CustomFieldDefinitionRepository;
import com.operion.customfield.CustomFieldService;
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
	private PaymentRepository paymentRepository;

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

	@Autowired
	private CustomFieldDefinitionRepository customFieldDefinitionRepository;

	@BeforeEach
	void setUpStudentService() {
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
			studentExitRepository, null, null, studentIdGenerator, new AuditLogService(auditLogRepository, new ObjectMapper()),
			new CustomFieldService(customFieldDefinitionRepository, new ObjectMapper()));
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

		FeeStructureGroup feeStructureGroup = feeService.createFeeStructureGroup("Grade 5 Annual Fees 2025-26", academicYear, schoolClass);
		FeeCategory feeCategory = feeService.createCategory("TUITION", "Tuition Fee", null, FeeCategoryType.GENERAL);
		FeeStructure feeStructure = feeService.createFeeStructure(feeStructureGroup, feeCategory, new BigDecimal("10000.00"),
				List.of(new InstallmentInput(1, LocalDate.of(2025, 6, 15), new BigDecimal("5000.00")),
						new InstallmentInput(2, LocalDate.of(2025, 10, 15), new BigDecimal("5000.00"))));

		StudentFeeAssignment assignment =
				feeService.assignFee(enrollment, feeStructure, new BigDecimal("1000.00"), "Sibling discount", 42L);
		List<FeeStructureInstallment> installments =
				feeStructureInstallmentRepository.findByFeeStructureIdOrderByInstallmentNumber(feeStructure.getId());

		return new Fixture(academicYear, assignment, installments);
	}

	@Autowired
	private StudentFeeAssignmentRepository studentFeeAssignmentRepository;

	@Test
	void bulkGenerationInvoicesOnlyDueInstallmentsAndIsSafeToRerun() {
		Fixture fixture = setUpFixture("fee-bulk-school", "ADM-310");
		BulkInvoiceService bulk = new BulkInvoiceService(studentFeeAssignmentRepository, feeStructureInstallmentRepository,
				invoiceRepository, feeService, org.mockito.Mockito.mock(FeeReminderService.class));
		Long yearId = fixture.academicYear().getId();
		LocalDate afterFirstDue = LocalDate.of(2025, 7, 1);

		BulkInvoiceService.Result preview = bulk.generateDue(yearId, null, afterFirstDue, true);
		assertThat(preview.toGenerate()).isEqualTo(1);
		assertThat(preview.generated()).isZero();
		assertThat(invoiceRepository.findByStudentFeeAssignmentId(fixture.assignment().getId())).isEmpty();

		BulkInvoiceService.Result first = bulk.generateDue(yearId, null, afterFirstDue, false);
		assertThat(first.generated()).isEqualTo(1);
		assertThat(first.failures()).isEmpty();

		BulkInvoiceService.Result rerun = bulk.generateDue(yearId, null, LocalDate.of(2026, 4, 30), false);
		assertThat(rerun.alreadyInvoiced()).isEqualTo(1);
		assertThat(rerun.generated()).isEqualTo(1);
		assertThat(invoiceRepository.findByStudentFeeAssignmentId(fixture.assignment().getId())).hasSize(2);
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
				.isInstanceOf(InvalidRequestException.class);

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
	void chequePaymentPersistsItsNumberAndBankName() {
		Fixture fixture = setUpFixture("fee-cheque-school", "ADM-304");
		Invoice invoice = feeService.generateInvoice(fixture.assignment(), fixture.installments().get(0));
		Payment payment = feeService.recordPayment(fixture.academicYear(), new BigDecimal("1000.00"), PaymentMethod.CHEQUE,
				LocalDate.of(2025, 6, 20), null, List.of(new AllocationInput(invoice.getId(), new BigDecimal("1000.00"))),
				"CHQ-000123", "State Bank");

		Payment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
		assertThat(reloaded.getPaymentReference()).isEqualTo("CHQ-000123");
		assertThat(reloaded.getPaymentBankName()).isEqualTo("State Bank");
	}

	@Test
	void bouncingAPaymentReversesAmountPaidWithoutDeletingTheAllocation() {
		Fixture fixture = setUpFixture("fee-bounce-school", "ADM-302");
		Invoice invoice = feeService.generateInvoice(fixture.assignment(), fixture.installments().get(0));
		Payment payment = feeService.recordPayment(fixture.academicYear(), new BigDecimal("4500.00"), PaymentMethod.CHEQUE,
				LocalDate.of(2025, 6, 20), null, List.of(new AllocationInput(invoice.getId(), new BigDecimal("4500.00"))),
				"CHQ-000124", "State Bank");

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
		Refund refund = feeService.recordRefund(payment, paidInvoice, new BigDecimal("1500.00"), "Overpayment", 9L, LocalDate.of(2025, 6, 25), null, null);

		assertThat(refund.getAmount()).isEqualByComparingTo("1500.00");
		Invoice reloadedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
		assertThat(reloadedInvoice.getAmountPaid()).isEqualByComparingTo("3000.00");
		assertThat(reloadedInvoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
	}

	@Test
	void feeOverviewRollsUpTotalsLedgerAndBreakdownsFromInvoices() {
		Fixture fixture = setUpFixture("fee-overview-school", "ADM-305");
		Invoice invoice1 = feeService.generateInvoice(fixture.assignment(), fixture.installments().get(0));
		feeService.generateInvoice(fixture.assignment(), fixture.installments().get(1));
		feeService.recordPayment(fixture.academicYear(), new BigDecimal("4500.00"), PaymentMethod.CASH, LocalDate.of(2025, 6, 20), null,
				List.of(new AllocationInput(invoice1.getId(), new BigDecimal("4500.00"))));

		FeeOverviewController controller = new FeeOverviewController(invoiceRepository);
		FeeOverviewResponse overview = controller.overview(fixture.academicYear().getId());

		assertThat(overview.totalFees()).isEqualByComparingTo("9000.00");
		assertThat(overview.collected()).isEqualByComparingTo("4500.00");
		assertThat(overview.outstanding()).isEqualByComparingTo("4500.00");
		// Both due dates are in 2025, so the unpaid second installment is overdue today.
		assertThat(overview.overdue()).isEqualByComparingTo("4500.00");

		assertThat(overview.ledger()).hasSize(1);
		FeeOverviewResponse.LedgerRow row = overview.ledger().get(0);
		assertThat(row.studentName()).isEqualTo("Meera Nair");
		assertThat(row.admissionNumber()).isEqualTo("ADM-305");
		assertThat(row.classLabel()).isEqualTo("Grade 5 - A");
		assertThat(row.paid()).isEqualByComparingTo("4500.00");
		assertThat(row.nextDueDate()).isEqualTo(LocalDate.of(2025, 10, 15));
		assertThat(row.status()).isEqualTo("OVERDUE");

		assertThat(overview.byClass()).hasSize(1);
		assertThat(overview.byClass().get(0).classLabel()).isEqualTo("Grade 5");
		assertThat(overview.byClass().get(0).percent()).isEqualTo(50);
		assertThat(overview.byType()).hasSize(1);
		assertThat(overview.byType().get(0).name()).isEqualTo("Tuition Fee");
		assertThat(overview.byType().get(0).amount()).isEqualByComparingTo("9000.00");
		assertThat(overview.trend()).hasSize(6);

		assertThat(controller.overview(fixture.academicYear().getId() + 999).ledger()).isEmpty();
	}
}
