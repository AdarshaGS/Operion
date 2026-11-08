package com.operion.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.audit.AuditLog;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.customfield.CustomFieldDefinitionRepository;
import com.operion.customfield.CustomFieldService;
import com.operion.finance.FeeService.AllocationInput;
import com.operion.finance.FeeService.InstallmentInput;
import com.operion.finance.AdmissionFeeService.FeeClearanceView;
import com.operion.finance.AdmissionFeeService.Preview;
import com.operion.common.InvalidRequestException;
import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.modules.AppModule;
import com.operion.modules.ModuleAccessService;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.AdmissionFeeClearanceMode;
import com.operion.student.AdmissionSettings;
import com.operion.student.AdmissionSettingsRepository;
import com.operion.student.FeeClearanceStatus;
import com.operion.student.Student;
import com.operion.student.StudentDocumentRepository;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentExitRepository;
import com.operion.student.StudentExitType;
import com.operion.student.StudentIdGenerator;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
import com.operion.student.api.AdmissionSettingsController;
import com.operion.student.api.AdmissionSettingsResponse;
import com.operion.student.api.UpdateAdmissionSettingsRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Real rows end to end (no mocks): enrolment through AdmissionFeeService assigns the "required at admission"
 * structures, invoices each one's earliest-due instalment, and tracks the enrollment's clearance status as
 * payments, waivers, bounces and refunds arrive. A bounce or refund never moves a CLEARED student back.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, FeeService.class, StudentIdGenerator.class, AdmissionFeeService.class,
		AdmissionFeeClearanceService.class, ModuleAccessService.class, AdmissionFeeClearanceTest.Wiring.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdmissionFeeClearanceTest {

	@TestConfiguration
	static class Wiring {
		@Bean
		AuditLogService auditLogService(AuditLogRepository repository) {
			return new AuditLogService(repository, new ObjectMapper());
		}

		@Bean
		StudentService studentService(StudentRepository studentRepository, StudentEnrollmentRepository studentEnrollmentRepository,
				StudentDocumentRepository studentDocumentRepository, StudentExitRepository studentExitRepository,
				SectionRepository sectionRepository, GradeLevelRepository gradeLevelRepository, StudentIdGenerator studentIdGenerator,
				AuditLogService auditLogService, CustomFieldDefinitionRepository customFieldDefinitionRepository) {
			return new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository, studentExitRepository,
					sectionRepository, gradeLevelRepository, null, null, studentIdGenerator, auditLogService,
					new CustomFieldService(customFieldDefinitionRepository, new ObjectMapper()));
		}
	}

	@Autowired private AdmissionFeeService admissionFeeService;
	@Autowired private AdmissionFeeClearanceService clearanceService;
	@Autowired private AuditLogRepository auditLogRepository;
	@Autowired private FeeService feeService;
	@Autowired private StudentService studentService;
	@Autowired private ModuleAccessService moduleAccessService;
	@Autowired private AdmissionSettingsRepository admissionSettingsRepository;
	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private CampusRepository campusRepository;
	@Autowired private AcademicYearRepository academicYearRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private GradeLevelRepository gradeLevelRepository;
	@Autowired private SchoolClassRepository schoolClassRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private StudentEnrollmentRepository studentEnrollmentRepository;
	@Autowired private StudentFeeAssignmentRepository studentFeeAssignmentRepository;
	@Autowired private InvoiceRepository invoiceRepository;
	@Autowired private FeeCategoryRepository feeCategoryRepository;

	private Organisation organisation;
	private AcademicYear year;
	private AcademicYear nextYear;
	private SchoolClass schoolClass;
	private Section section;
	private FeeStructureGroup group;
	private int sequence;

	/** Required structure: instalment 1 is due LATER than instalment 2, so "earliest-due" differs from "number 1". */
	private FeeStructure tuition;
	private FeeStructure admission;
	private FeeStructure bus;

	@BeforeEach
	void setUp() {
		TenantContext.clear();
		organisation = organisationRepository.save(new Organisation("Admission School", "Admission Trust", "admission-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);

		year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		nextYear = academicYearRepository.save(new AcademicYear("2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		GradeLevel grade6 = gradeLevelRepository.save(new GradeLevel("Grade 6", 6, null));
		schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade5, null));
		section = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		SchoolClass nextClass = schoolClassRepository.save(new SchoolClass(nextYear, campus, grade6, null));
		nextSection = sectionRepository.save(new Section(nextClass, "A", 40, null));

		group = feeService.createFeeStructureGroup("Grade 5 Fees", year, schoolClass);
		tuition = feeService.createFeeStructure(group, feeService.createCategory("TUITION", "Tuition Fee", null, FeeCategoryType.GENERAL, true),
				new BigDecimal("10000.00"), List.of(new InstallmentInput(1, LocalDate.of(2025, 8, 1), new BigDecimal("4000.00")),
						new InstallmentInput(2, LocalDate.of(2025, 6, 15), new BigDecimal("6000.00"))));
		admission = feeService.createFeeStructure(group, feeService.createCategory("ADMISSION", "Admission Fee", null, FeeCategoryType.GENERAL, true),
				new BigDecimal("1000.00"), List.of(new InstallmentInput(1, LocalDate.of(2025, 6, 20), new BigDecimal("1000.00"))));
		bus = feeService.createFeeStructure(group, feeService.createCategory("BUS", "Bus Fee", null, FeeCategoryType.TRANSPORT),
				new BigDecimal("5000.00"), List.of(new InstallmentInput(1, LocalDate.of(2025, 6, 10), new BigDecimal("5000.00"))));
	}

	private Section nextSection;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private void setMode(AdmissionFeeClearanceMode mode) {
		AdmissionSettings settings = admissionSettingsRepository.findByOrganisationId(organisation.getId()).orElseGet(AdmissionSettings::new);
		settings.setFeeClearanceMode(mode);
		admissionSettingsRepository.save(settings);
	}

	private Student admit() {
		int n = ++sequence;
		Person person = personRepository.save(new Person("Student" + n, "Test"));
		return studentService.admit(person, "ADM-" + System.nanoTime() + "-" + n, LocalDate.of(2025, 5, 1), null, null, null, null, null, null,
				"Indian", null, null, null, null);
	}

	private StudentEnrollment enrol(Student student) {
		return admissionFeeService.enroll(student, year, section, ++sequence, LocalDate.of(2025, 6, 1));
	}

	private StudentEnrollment reload(StudentEnrollment enrollment) {
		return studentEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
	}

	private FeeClearanceStatus statusOf(StudentEnrollment enrollment) {
		return reload(enrollment).getFeeClearanceStatus();
	}

	private List<Invoice> admissionInvoices(StudentEnrollment enrollment) {
		return invoiceRepository.findAdmissionInvoices(enrollment.getId());
	}

	private Invoice invoiceFor(StudentEnrollment enrollment, FeeStructure structure) {
		return admissionInvoices(enrollment).stream()
				.filter(i -> i.getStudentFeeAssignment().getFeeStructure().getId().equals(structure.getId()))
				.findFirst().orElseThrow();
	}

	private Payment pay(Invoice invoice, String amount, PaymentMethod method) {
		BigDecimal value = new BigDecimal(amount);
		boolean cheque = method == PaymentMethod.CHEQUE;
		return feeService.recordPayment(year, value, method, LocalDate.of(2025, 6, 2), null,
				List.of(new AllocationInput(invoice.getId(), value)), cheque ? "CHQ-" + System.nanoTime() : null, cheque ? "Test Bank" : null);
	}

	private Invoice fresh(Invoice invoice) {
		return invoiceRepository.findById(invoice.getId()).orElseThrow();
	}

	// 1
	@Test
	void aNewEnrollmentDefaultsToNotRequired() {
		StudentEnrollment plain = studentService.enroll(admit(), year, section, 1, LocalDate.of(2025, 6, 1));
		assertThat(statusOf(plain)).isEqualTo(FeeClearanceStatus.NOT_REQUIRED);
		assertThat(admissionSettingsRepository.findByOrganisationId(organisation.getId())).isEmpty();
	}

	// 2
	@Test
	void modeOffEnrolsPlainlyWithNothingAssigned() {
		StudentEnrollment enrollment = enrol(admit());
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.NOT_REQUIRED);
		assertThat(studentFeeAssignmentRepository.findByStudentEnrollmentId(enrollment.getId())).isEmpty();

		setMode(AdmissionFeeClearanceMode.OFF);
		StudentEnrollment explicitOff = enrol(admit());
		assertThat(studentFeeAssignmentRepository.findByStudentEnrollmentId(explicitOff.getId())).isEmpty();
		assertThat(admissionFeeService.preview(year, schoolClass).structures()).isEmpty();
	}

	// 3
	@Test
	void requiredStructuresAreAssignedAndOnlyTheEarliestDueInstalmentIsInvoiced() {
		setMode(AdmissionFeeClearanceMode.TRACK);
		StudentEnrollment enrollment = enrol(admit());

		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.PENDING);
		List<StudentFeeAssignment> assignments = studentFeeAssignmentRepository.findByStudentEnrollmentId(enrollment.getId());
		assertThat(assignments).extracting(a -> a.getFeeStructure().getId())
				.containsExactlyInAnyOrder(tuition.getId(), admission.getId());
		assertThat(assignments).noneMatch(a -> a.getFeeStructure().getId().equals(bus.getId()));

		List<Invoice> invoices = admissionInvoices(enrollment);
		assertThat(invoices).hasSize(2).allMatch(Invoice::isAdmissionInvoice);
		// Tuition's earliest-due instalment is #2 (6000, due 15 Jun), not #1.
		assertThat(invoiceFor(enrollment, tuition).getTotalAmount()).isEqualByComparingTo("6000.00");
		assertThat(invoiceFor(enrollment, admission).getTotalAmount()).isEqualByComparingTo("1000.00");
		assertThat(invoiceRepository.findAll()).hasSize(2);
	}

	@Test
	void blockModeRunsTheSameWorkflowAsTrack() {
		setMode(AdmissionFeeClearanceMode.BLOCK);
		StudentEnrollment enrollment = enrol(admit());
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.PENDING);
		assertThat(admissionInvoices(enrollment)).hasSize(2);
	}

	// 4
	@Test
	void aClassWithNoRequiredStructureIsNotRequired() {
		setMode(AdmissionFeeClearanceMode.TRACK);
		feeService.setCategoryRequiredAtAdmission(feeCategoryRepository.findAll().stream().filter(c -> c.getCode().equals("TUITION")).findFirst().orElseThrow(), false);
		feeService.setCategoryRequiredAtAdmission(feeCategoryRepository.findAll().stream().filter(c -> c.getCode().equals("ADMISSION")).findFirst().orElseThrow(), false);

		StudentEnrollment enrollment = enrol(admit());
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.NOT_REQUIRED);
		assertThat(studentFeeAssignmentRepository.findByStudentEnrollmentId(enrollment.getId())).isEmpty();
		assertThat(admissionFeeService.preview(year, schoolClass).structures()).isEmpty();
	}

	// 5
	@Test
	void feesModuleOffMeansNoFeesEvenInTrackMode() {
		setMode(AdmissionFeeClearanceMode.TRACK);
		moduleAccessService.setEnabled(organisation.getId(), AppModule.FEES, false, "test");

		StudentEnrollment enrollment = enrol(admit());
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.NOT_REQUIRED);
		assertThat(studentFeeAssignmentRepository.findByStudentEnrollmentId(enrollment.getId())).isEmpty();
		assertThat(admissionFeeService.preview(year, schoolClass).structures()).isEmpty();
	}

	// 6 - a fully discounted assignment that already exists (from before the exit) re-invoices at zero total
	@Test
	void aZeroTotalAdmissionInvoiceClearsImmediately() {
		StudentEnrollment first = enrol(admit());
		feeService.assignFee(first, admission, new BigDecimal("1000.00"), "Staff child", 42L);
		feeService.assignFee(first, tuition, new BigDecimal("10000.00"), "Staff child", 42L);
		studentService.recordExit(first.getStudent(), StudentExitType.WITHDRAWAL, LocalDate.of(2025, 6, 5), "moved", null, null);

		setMode(AdmissionFeeClearanceMode.TRACK);
		StudentEnrollment again = admissionFeeService.enroll(first.getStudent(), year, section, 7, LocalDate.of(2025, 6, 10));

		assertThat(again.getId()).isEqualTo(first.getId());
		assertThat(admissionInvoices(again)).hasSize(2).allMatch(i -> i.getTotalAmount().signum() == 0);
		assertThat(statusOf(again)).isEqualTo(FeeClearanceStatus.CLEARED);
	}

	// 7
	@Test
	void aPartPaymentStaysPendingAndTheFullPaymentClears() {
		setMode(AdmissionFeeClearanceMode.TRACK);
		StudentEnrollment enrollment = enrol(admit());
		Invoice tuitionInvoice = invoiceFor(enrollment, tuition);
		Invoice admissionInvoice = invoiceFor(enrollment, admission);

		pay(tuitionInvoice, "6000.00", PaymentMethod.CASH);
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.PENDING);

		pay(admissionInvoice, "400.00", PaymentMethod.CASH);
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.PENDING);

		pay(admissionInvoice, "600.00", PaymentMethod.CASH);
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.CLEARED);
		assertThat(reload(enrollment).getFeeClearanceChangedAt()).isNotNull();
	}

	// 8 - the gateway webhook records its payment through feeService.recordPayment with method ONLINE
	@Test
	void anOnlinePaymentClearsLikeAnyOther() {
		setMode(AdmissionFeeClearanceMode.TRACK);
		StudentEnrollment enrollment = enrol(admit());
		pay(invoiceFor(enrollment, tuition), "6000.00", PaymentMethod.ONLINE);
		pay(invoiceFor(enrollment, admission), "1000.00", PaymentMethod.ONLINE);
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.CLEARED);
	}

	// 9 - the owner's rule: after clearance a bounce or refund does NOT put the student back
	@Test
	void aBounceOrRefundAfterClearanceLeavesTheStudentCleared() {
		setMode(AdmissionFeeClearanceMode.TRACK);
		StudentEnrollment enrollment = enrol(admit());
		Payment tuitionPayment = pay(invoiceFor(enrollment, tuition), "6000.00", PaymentMethod.CHEQUE);
		Payment admissionPayment = pay(invoiceFor(enrollment, admission), "1000.00", PaymentMethod.CASH);
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.CLEARED);

		feeService.bouncePayment(tuitionPayment);
		assertThat(fresh(invoiceFor(enrollment, tuition)).getOutstanding()).isEqualByComparingTo("6000.00");
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.CLEARED);

		feeService.recordRefund(admissionPayment, invoiceFor(enrollment, admission), new BigDecimal("1000.00"), "Withdrawn", 42L,
				LocalDate.of(2025, 6, 3), null, null);
		assertThat(fresh(invoiceFor(enrollment, admission)).getOutstanding()).isEqualByComparingTo("1000.00");
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.CLEARED);
	}

	@Test
	void aBounceBeforeClearanceKeepsThePendingStudentPending() {
		setMode(AdmissionFeeClearanceMode.TRACK);
		StudentEnrollment enrollment = enrol(admit());
		Payment payment = pay(invoiceFor(enrollment, tuition), "6000.00", PaymentMethod.CHEQUE);
		feeService.bouncePayment(payment);
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.PENDING);
	}

	@Test
	void waivingTheRemainderClears() {
		setMode(AdmissionFeeClearanceMode.TRACK);
		StudentEnrollment enrollment = enrol(admit());
		pay(invoiceFor(enrollment, tuition), "6000.00", PaymentMethod.CASH);
		pay(invoiceFor(enrollment, admission), "400.00", PaymentMethod.CASH);
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.PENDING);

		feeService.recordWaiver(invoiceFor(enrollment, admission), new BigDecimal("600.00"), "Hardship", 42L, LocalDate.of(2025, 6, 3), null, null);
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.CLEARED);
	}

	// 10
	@Test
	void anExemptedStudentIsNeverMoved() {
		setMode(AdmissionFeeClearanceMode.TRACK);
		StudentEnrollment enrollment = enrol(admit());
		StudentEnrollment stored = reload(enrollment);
		stored.changeFeeClearanceStatus(FeeClearanceStatus.EXEMPTED);
		studentEnrollmentRepository.save(stored);

		Payment payment = pay(invoiceFor(enrollment, tuition), "6000.00", PaymentMethod.CHEQUE);
		pay(invoiceFor(enrollment, admission), "1000.00", PaymentMethod.CASH);
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.EXEMPTED);

		feeService.bouncePayment(payment);
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.EXEMPTED);
	}

	// 11
	@Test
	void promotionAndCsvStyleEnrolmentStayNotRequired() {
		setMode(AdmissionFeeClearanceMode.TRACK);
		Student student = admit();
		// Straight through StudentService, as promote() and the enrolment CSV import both do.
		StudentEnrollment viaImport = studentService.enroll(student, year, section, 3, LocalDate.of(2025, 6, 1));
		assertThat(statusOf(viaImport)).isEqualTo(FeeClearanceStatus.NOT_REQUIRED);
		assertThat(studentFeeAssignmentRepository.findByStudentEnrollmentId(viaImport.getId())).isEmpty();

		StudentEnrollment promoted = studentService.promote(student, nextYear, nextSection, null, LocalDate.of(2026, 6, 1), false);
		assertThat(statusOf(promoted)).isEqualTo(FeeClearanceStatus.NOT_REQUIRED);
		assertThat(studentFeeAssignmentRepository.findByStudentEnrollmentId(promoted.getId())).isEmpty();
		assertThat(invoiceRepository.findAll()).isEmpty();
	}

	// 12
	@Test
	void reEnrolmentInTheSameYearReusesTheAssignmentsAndInvoices() {
		setMode(AdmissionFeeClearanceMode.TRACK);
		Student student = admit();
		StudentEnrollment first = enrol(student);
		pay(invoiceFor(first, tuition), "6000.00", PaymentMethod.CASH);
		studentService.recordExit(student, StudentExitType.WITHDRAWAL, LocalDate.of(2025, 6, 5), "moved", null, null);

		StudentEnrollment again = admissionFeeService.enroll(student, year, section, 9, LocalDate.of(2025, 6, 10));

		assertThat(again.getId()).isEqualTo(first.getId());
		assertThat(studentFeeAssignmentRepository.findByStudentEnrollmentId(again.getId())).hasSize(2);
		assertThat(admissionInvoices(again)).hasSize(2);
		assertThat(statusOf(again)).isEqualTo(FeeClearanceStatus.PENDING);
	}

	// 13 (converted: the applicant convert action is out of Phase 1) - a failed enrolment leaves nothing behind
	@Test
	void aFailedEnrolmentRollsBackEverything() {
		setMode(AdmissionFeeClearanceMode.TRACK);
		Section tiny = sectionRepository.save(new Section(schoolClass, "B", 1, null));
		admissionFeeService.enroll(admit(), year, tiny, 1, LocalDate.of(2025, 6, 1));
		long assignmentsBefore = studentFeeAssignmentRepository.count();
		long invoicesBefore = invoiceRepository.count();
		long enrollmentsBefore = studentEnrollmentRepository.count();

		Student second = admit();
		assertThatThrownBy(() -> admissionFeeService.enroll(second, year, tiny, 2, LocalDate.of(2025, 6, 1)))
				.isInstanceOf(RuntimeException.class);

		assertThat(studentFeeAssignmentRepository.count()).isEqualTo(assignmentsBefore);
		assertThat(invoiceRepository.count()).isEqualTo(invoicesBefore);
		assertThat(studentEnrollmentRepository.count()).isEqualTo(enrollmentsBefore);
	}

	// 14
	@Test
	void concurrentPaymentsOnTwoAdmissionInvoicesEndCleared() throws Exception {
		setMode(AdmissionFeeClearanceMode.TRACK);
		StudentEnrollment enrollment = enrol(admit());
		Invoice tuitionInvoice = invoiceFor(enrollment, tuition);
		Invoice admissionInvoice = invoiceFor(enrollment, admission);
		Long orgId = organisation.getId();

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<?> a = pool.submit(() -> concurrentPay(orgId, start, tuitionInvoice, "6000.00"));
			Future<?> b = pool.submit(() -> concurrentPay(orgId, start, admissionInvoice, "1000.00"));
			start.countDown();
			a.get(30, TimeUnit.SECONDS);
			b.get(30, TimeUnit.SECONDS);
		} finally {
			pool.shutdownNow();
		}

		assertThat(fresh(tuitionInvoice).getOutstanding()).isEqualByComparingTo("0.00");
		assertThat(fresh(admissionInvoice).getOutstanding()).isEqualByComparingTo("0.00");
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.CLEARED);
	}

	private void concurrentPay(Long orgId, CountDownLatch start, Invoice invoice, String amount) {
		TenantContext.set(orgId, null);
		try {
			start.await();
			pay(invoice, amount, PaymentMethod.CASH);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			TenantContext.clear();
		}
	}

	// preview + the fee-clearance view
	@Test
	void previewListsOnlyTheRequiredStructuresWithTheirFirstInstalment() {
		setMode(AdmissionFeeClearanceMode.TRACK);
		Preview preview = admissionFeeService.preview(year, schoolClass);

		assertThat(preview.mode()).isEqualTo("TRACK");
		assertThat(preview.structures()).extracting(l -> l.categoryName()).containsExactlyInAnyOrder("Tuition Fee", "Admission Fee");
		assertThat(preview.admissionTotal()).isEqualByComparingTo("7000.00");
		assertThat(preview.structures().stream().filter(l -> l.feeStructureId().equals(tuition.getId())).findFirst().orElseThrow()
				.firstInstallment().number()).isEqualTo(2);
	}

	@Test
	void theClearanceViewReportsStepsAndInvoices() {
		setMode(AdmissionFeeClearanceMode.TRACK);
		StudentEnrollment enrollment = enrol(admit());

		FeeClearanceView pending = admissionFeeService.describe(reload(enrollment));
		assertThat(pending.status()).isEqualTo("PENDING");
		assertThat(pending.invoices()).hasSize(2);
		assertThat(pending.steps()).extracting(s -> s.key() + ":" + s.done())
				.containsExactly("ENROLLED:true", "FEES_ASSIGNED:true", "INVOICES_CREATED:true", "FIRST_PAYMENT:false");

		pay(invoiceFor(enrollment, tuition), "6000.00", PaymentMethod.CASH);
		pay(invoiceFor(enrollment, admission), "1000.00", PaymentMethod.CASH);
		FeeClearanceView cleared = admissionFeeService.describe(reload(enrollment));
		assertThat(cleared.status()).isEqualTo("CLEARED");
		assertThat(cleared.steps().get(3).done()).isTrue();
	}

	// settings + category flag
	@Test
	void admissionSettingsDefaultToOffAndPutUpserts() {
		AdmissionSettingsController controller = new AdmissionSettingsController(admissionSettingsRepository);
		assertThat(controller.get().feeClearanceMode()).isEqualTo("OFF");

		AdmissionSettingsResponse saved = controller.update(new UpdateAdmissionSettingsRequest("TRACK"));
		assertThat(saved.feeClearanceMode()).isEqualTo("TRACK");
		assertThat(controller.get().feeClearanceMode()).isEqualTo("TRACK");

		controller.update(new UpdateAdmissionSettingsRequest("BLOCK"));
		assertThat(admissionSettingsRepository.findAll()).hasSize(1);
		assertThat(controller.get().feeClearanceMode()).isEqualTo("BLOCK");
		assertThatThrownBy(() -> controller.update(new UpdateAdmissionSettingsRequest("SOMETIMES"))).isInstanceOf(RuntimeException.class);
	}

	@Test
	void theRequiredAtAdmissionFlagDefaultsToFalseAndCanBeChanged() {
		FeeCategory plain = feeService.createCategory("LAB", "Lab Fee", null, FeeCategoryType.GENERAL);
		assertThat(plain.isRequiredAtAdmission()).isFalse();
		FeeCategory flagged = feeService.createCategory("KIT", "Kit Fee", null, FeeCategoryType.GENERAL, true);
		assertThat(feeCategoryRepository.findById(flagged.getId()).orElseThrow().isRequiredAtAdmission()).isTrue();

		feeService.setCategoryRequiredAtAdmission(plain, true);
		assertThat(feeCategoryRepository.findById(plain.getId()).orElseThrow().isRequiredAtAdmission()).isTrue();
		feeService.setCategoryRequiredAtAdmission(plain, false);
		assertThat(feeCategoryRepository.findById(plain.getId()).orElseThrow().isRequiredAtAdmission()).isFalse();
	}

	// ---- manual override and revoke ----

	private List<AuditLog> auditRows(StudentEnrollment enrollment, String action) {
		return auditLogRepository.findAll().stream()
				.filter(a -> "StudentEnrollment".equals(a.getEntityType()) && enrollment.getId().equals(a.getEntityId()) && action.equals(a.getAction()))
				.toList();
	}

	@Test
	void overrideRequiresANonBlankReasonAndLeavesThePendingStudentUntouched() {
		setMode(AdmissionFeeClearanceMode.BLOCK);
		StudentEnrollment enrollment = enrol(admit());

		assertThatThrownBy(() -> clearanceService.override(enrollment.getId(), null)).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> clearanceService.override(enrollment.getId(), "")).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> clearanceService.override(enrollment.getId(), "   ")).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> clearanceService.override(enrollment.getId(), "x".repeat(501))).isInstanceOf(InvalidRequestException.class);

		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.PENDING);
		assertThat(auditRows(enrollment, "FEE_CLEARANCE_OVERRIDDEN")).isEmpty();
	}

	@Test
	void overrideExemptsThePendingStudentRecordsWhoWhenWhyAndAudits() {
		setMode(AdmissionFeeClearanceMode.BLOCK);
		StudentEnrollment enrollment = enrol(admit());
		TenantContext.set(organisation.getId(), 77L);

		clearanceService.override(enrollment.getId(), "  Sibling of staff, principal approved  ");

		StudentEnrollment stored = reload(enrollment);
		assertThat(stored.getFeeClearanceStatus()).isEqualTo(FeeClearanceStatus.EXEMPTED);
		assertThat(stored.getFeeClearanceOverrideBy()).isEqualTo(77L);
		assertThat(stored.getFeeClearanceOverrideReason()).isEqualTo("Sibling of staff, principal approved");
		assertThat(stored.getFeeClearanceChangedAt()).isNotNull();

		List<AuditLog> rows = auditRows(enrollment, "FEE_CLEARANCE_OVERRIDDEN");
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).getActorUserId()).isEqualTo(77L);
		assertThat(rows.get(0).getBeforeValue()).contains("PENDING");
		assertThat(rows.get(0).getAfterValue()).contains("EXEMPTED").contains("Sibling of staff, principal approved");

		FeeClearanceView view = admissionFeeService.describe(stored);
		assertThat(view.status()).isEqualTo("EXEMPTED");
		assertThat(view.override()).isNotNull();
		assertThat(view.override().by()).isEqualTo(77L);
		assertThat(view.override().at()).isNotNull();
		assertThat(view.override().reason()).isEqualTo("Sibling of staff, principal approved");
	}

	@Test
	void onlyAPendingEnrollmentCanBeOverridden() {
		setMode(AdmissionFeeClearanceMode.BLOCK);
		StudentEnrollment notRequired = studentService.enroll(admit(), year, section, 1, LocalDate.of(2025, 6, 1));
		assertThatThrownBy(() -> clearanceService.override(notRequired.getId(), "reason")).isInstanceOf(IllegalStateException.class);
		assertThat(statusOf(notRequired)).isEqualTo(FeeClearanceStatus.NOT_REQUIRED);

		StudentEnrollment cleared = enrol(admit());
		pay(invoiceFor(cleared, tuition), "6000.00", PaymentMethod.CASH);
		pay(invoiceFor(cleared, admission), "1000.00", PaymentMethod.CASH);
		assertThatThrownBy(() -> clearanceService.override(cleared.getId(), "reason")).isInstanceOf(IllegalStateException.class);
		assertThat(statusOf(cleared)).isEqualTo(FeeClearanceStatus.CLEARED);

		StudentEnrollment exempted = enrol(admit());
		clearanceService.override(exempted.getId(), "first");
		assertThatThrownBy(() -> clearanceService.override(exempted.getId(), "second")).isInstanceOf(IllegalStateException.class);
		assertThat(reload(exempted).getFeeClearanceOverrideReason()).isEqualTo("first");
		assertThat(auditRows(exempted, "FEE_CLEARANCE_OVERRIDDEN")).hasSize(1);
	}

	@Test
	void revokeReturnsToPendingWhenTheInvoicesAreStillOwedAndClearsTheOverride() {
		setMode(AdmissionFeeClearanceMode.BLOCK);
		StudentEnrollment enrollment = enrol(admit());
		TenantContext.set(organisation.getId(), 77L);
		clearanceService.override(enrollment.getId(), "Principal approved");

		clearanceService.revokeOverride(enrollment.getId());

		StudentEnrollment stored = reload(enrollment);
		assertThat(stored.getFeeClearanceStatus()).isEqualTo(FeeClearanceStatus.PENDING);
		assertThat(stored.getFeeClearanceOverrideBy()).isNull();
		assertThat(stored.getFeeClearanceOverrideReason()).isNull();
		assertThat(admissionFeeService.describe(stored).override()).isNull();

		List<AuditLog> rows = auditRows(enrollment, "FEE_CLEARANCE_OVERRIDE_REVOKED");
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).getBeforeValue()).contains("EXEMPTED").contains("Principal approved");
		assertThat(rows.get(0).getAfterValue()).contains("PENDING");
	}

	@Test
	void revokeGoesStraightToClearedWhenEveryAdmissionInvoiceWasPaidMeanwhile() {
		setMode(AdmissionFeeClearanceMode.BLOCK);
		StudentEnrollment enrollment = enrol(admit());
		clearanceService.override(enrollment.getId(), "Pay later");
		pay(invoiceFor(enrollment, tuition), "6000.00", PaymentMethod.CASH);
		pay(invoiceFor(enrollment, admission), "1000.00", PaymentMethod.CASH);
		// Payments never move an EXEMPTED student; only the revoke recalculates.
		assertThat(statusOf(enrollment)).isEqualTo(FeeClearanceStatus.EXEMPTED);

		clearanceService.revokeOverride(enrollment.getId());

		StudentEnrollment stored = reload(enrollment);
		assertThat(stored.getFeeClearanceStatus()).isEqualTo(FeeClearanceStatus.CLEARED);
		assertThat(stored.getFeeClearanceOverrideReason()).isNull();
		assertThat(auditRows(enrollment, "FEE_CLEARANCE_OVERRIDE_REVOKED")).hasSize(1);
		assertThat(auditRows(enrollment, "FEE_CLEARANCE_OVERRIDE_REVOKED").get(0).getAfterValue()).contains("CLEARED");
	}

	@Test
	void onlyAnExemptedEnrollmentCanBeRevoked() {
		setMode(AdmissionFeeClearanceMode.BLOCK);
		StudentEnrollment pending = enrol(admit());
		assertThatThrownBy(() -> clearanceService.revokeOverride(pending.getId())).isInstanceOf(IllegalStateException.class);
		assertThat(statusOf(pending)).isEqualTo(FeeClearanceStatus.PENDING);

		StudentEnrollment notRequired = studentService.enroll(admit(), year, section, 1, LocalDate.of(2025, 6, 1));
		assertThatThrownBy(() -> clearanceService.revokeOverride(notRequired.getId())).isInstanceOf(IllegalStateException.class);
		assertThat(auditRows(pending, "FEE_CLEARANCE_OVERRIDE_REVOKED")).isEmpty();
	}

	@Test
	void theOverrideEndpointsRequireTheOverridePermission() throws Exception {
		for (String method : List.of("override", "revokeOverride")) {
			var found = java.util.Arrays.stream(com.operion.finance.api.FeeClearanceController.class.getDeclaredMethods())
					.filter(m -> m.getName().equals(method)).findFirst().orElseThrow();
			assertThat(found.getAnnotation(RequirePermission.class)).isNotNull();
			assertThat(found.getAnnotation(RequirePermission.class).value()).isEqualTo("FEE_ADMISSION_CLEARANCE_OVERRIDE");
		}
	}
}
