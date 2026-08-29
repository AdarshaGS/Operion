package com.operion.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
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
 * Proves the "pay this invoice" link lifecycle end to end against a hand-written
 * RazorpayGateway stub (this codebase's established pattern for external dependencies,
 * e.g. AuditLogService in other @DataJpaTest slices) - never a real HTTP call. The
 * webhook path is the important one: only it ever calls FeeService.recordPayment(), it
 * must be idempotent against redelivery, and it must resolve the correct organisation
 * from nothing but the gateway's own order id (see
 * PaymentGatewayOrderRepository.findOrganisationIdByGatewayOrderId).
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, FeeService.class, StudentIdGenerator.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FeePaymentGatewayServiceTest {

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
	private PaymentGatewayOrderRepository paymentGatewayOrderRepository;

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

	private final StubRazorpayGateway gateway = new StubRazorpayGateway();

	@BeforeEach
	void setUpStudentService() {
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
			studentExitRepository, null, null, studentIdGenerator, new AuditLogService(auditLogRepository, new ObjectMapper()));
	}

	private FeePaymentGatewayService gatewayService() {
		RazorpayCredentialsProvider credentialsProvider =
				new RazorpayCredentialsProvider("test-key-id", "test-key-secret", "test-webhook-secret");
		return new FeePaymentGatewayService(
				paymentGatewayOrderRepository, invoiceRepository, organisationRepository, feeService, credentialsProvider, gateway);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(Organisation organisation, Invoice invoice) {
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
		Person person = personRepository.save(new Person("Arjun", "Rao"));

		Student student = studentService.admit(
				person, admissionNumber, LocalDate.of(2025, 5, 1), null, null, null, null, null, null, "Indian", null, null, null, null);
		StudentEnrollment enrollment = studentService.enroll(student, academicYear, section, 7, LocalDate.of(2025, 6, 1));

		FeeCategory feeCategory = feeService.createCategory("TUITION", "Tuition Fee", null);
		FeeStructure feeStructure = feeService.createFeeStructure(academicYear, schoolClass, feeCategory, new BigDecimal("5000.00"),
				List.of(new InstallmentInput(1, LocalDate.of(2025, 6, 15), new BigDecimal("5000.00"))));
		StudentFeeAssignment assignment = feeService.assignFee(enrollment, feeStructure, null, null, null);
		FeeStructureInstallment installment =
				feeStructureInstallmentRepository.findByFeeStructureIdOrderByInstallmentNumber(feeStructure.getId()).get(0);
		Invoice invoice = feeService.generateInvoice(assignment, installment);

		return new Fixture(organisation, invoice);
	}

	@Test
	void createLinkRejectsAnInvoiceWithNothingOutstanding() {
		Fixture fixture = setUpFixture("pgo-zero-outstanding", "ADM-PGO-1");
		feeService.recordPayment(fixture.invoice().getAcademicYear(), new BigDecimal("5000.00"), PaymentMethod.CASH,
				LocalDate.of(2025, 6, 20), null, List.of(new FeeService.AllocationInput(fixture.invoice().getId(), new BigDecimal("5000.00"))));

		assertThatThrownBy(() -> gatewayService().createLink(fixture.invoice().getId())).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void checkoutCreatesAGatewayOrderAgainstCurrentOutstanding() {
		Fixture fixture = setUpFixture("pgo-checkout", "ADM-PGO-2");
		FeePaymentGatewayService.IssuedLink link = gatewayService().createLink(fixture.invoice().getId());
		TenantContext.clear();

		FeePaymentGatewayService.CheckoutSession session = gatewayService().initiateCheckout(link.organisationSlug(), link.linkToken());

		assertThat(session.amountInPaise()).isEqualTo(500_000L);
		assertThat(session.gatewayOrderId()).startsWith("order_stub_");
		assertThat(session.razorpayKeyId()).isEqualTo("test-key-id");

		TenantContext.set(fixture.organisation().getId(), null);
		PaymentGatewayOrder stored = paymentGatewayOrderRepository.findByLinkToken(link.linkToken()).orElseThrow();
		assertThat(stored.getGatewayOrderId()).isEqualTo(session.gatewayOrderId());
		assertThat(stored.getAmount()).isEqualByComparingTo("5000.00");
	}

	@Test
	void checkoutRejectsAnExpiredLink() {
		Fixture fixture = setUpFixture("pgo-expired", "ADM-PGO-3");
		PaymentGatewayOrder expired =
				paymentGatewayOrderRepository.save(new PaymentGatewayOrder(fixture.invoice(), "expired-token-xyz", Instant.now().minusSeconds(60)));
		String slug = fixture.organisation().getSlug();
		TenantContext.clear();

		assertThatThrownBy(() -> gatewayService().initiateCheckout(slug, expired.getLinkToken())).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void webhookWithAValidSignatureRecordsThePaymentAndMarksTheOrderPaid() {
		Fixture fixture = setUpFixture("pgo-webhook-happy", "ADM-PGO-4");
		FeePaymentGatewayService.IssuedLink link = gatewayService().createLink(fixture.invoice().getId());
		gatewayService().initiateCheckout(link.organisationSlug(), link.linkToken());
		TenantContext.set(fixture.organisation().getId(), null);
		String gatewayOrderId = paymentGatewayOrderRepository.findByLinkToken(link.linkToken()).orElseThrow().getGatewayOrderId();
		TenantContext.clear();

		gateway.signatureValid = true;
		gatewayService().handleWebhook("{}", "sig", "payment.captured", gatewayOrderId, "pay_stub_1");

		TenantContext.set(fixture.organisation().getId(), null);
		Invoice reloadedInvoice = invoiceRepository.findById(fixture.invoice().getId()).orElseThrow();
		assertThat(reloadedInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
		assertThat(reloadedInvoice.getAmountPaid()).isEqualByComparingTo("5000.00");
		PaymentGatewayOrder order = paymentGatewayOrderRepository.findByGatewayOrderId(gatewayOrderId).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(PaymentGatewayOrderStatus.PAID);
		assertThat(order.getPayment()).isNotNull();
		assertThat(order.getPayment().getPaymentMethod()).isEqualTo(PaymentMethod.ONLINE);
	}

	@Test
	void redeliveringTheSameWebhookEventIsIdempotent() {
		Fixture fixture = setUpFixture("pgo-webhook-idempotent", "ADM-PGO-5");
		FeePaymentGatewayService.IssuedLink link = gatewayService().createLink(fixture.invoice().getId());
		gatewayService().initiateCheckout(link.organisationSlug(), link.linkToken());
		TenantContext.set(fixture.organisation().getId(), null);
		String gatewayOrderId = paymentGatewayOrderRepository.findByLinkToken(link.linkToken()).orElseThrow().getGatewayOrderId();
		TenantContext.clear();

		gatewayService().handleWebhook("{}", "sig", "payment.captured", gatewayOrderId, "pay_stub_2");
		gatewayService().handleWebhook("{}", "sig", "payment.captured", gatewayOrderId, "pay_stub_2");

		TenantContext.set(fixture.organisation().getId(), null);
		Invoice reloadedInvoice = invoiceRepository.findById(fixture.invoice().getId()).orElseThrow();
		// A second recordPayment would have double-applied - amountPaid must still equal
		// exactly one payment's worth, not two.
		assertThat(reloadedInvoice.getAmountPaid()).isEqualByComparingTo("5000.00");
	}

	@Test
	void webhookWithAnInvalidSignatureRecordsNothing() {
		Fixture fixture = setUpFixture("pgo-webhook-bad-sig", "ADM-PGO-6");
		FeePaymentGatewayService.IssuedLink link = gatewayService().createLink(fixture.invoice().getId());
		gatewayService().initiateCheckout(link.organisationSlug(), link.linkToken());
		TenantContext.set(fixture.organisation().getId(), null);
		String gatewayOrderId = paymentGatewayOrderRepository.findByLinkToken(link.linkToken()).orElseThrow().getGatewayOrderId();
		TenantContext.clear();

		gateway.signatureValid = false;
		assertThatThrownBy(() -> gatewayService().handleWebhook("{}", "bad-sig", "payment.captured", gatewayOrderId, "pay_stub_3"))
				.isInstanceOf(WebhookVerificationException.class);

		TenantContext.set(fixture.organisation().getId(), null);
		assertThat(invoiceRepository.findById(fixture.invoice().getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.ISSUED);
	}

	@Test
	void webhookIgnoresEventTypesOtherThanPaymentCaptured() {
		Fixture fixture = setUpFixture("pgo-webhook-other-event", "ADM-PGO-7");
		FeePaymentGatewayService.IssuedLink link = gatewayService().createLink(fixture.invoice().getId());
		gatewayService().initiateCheckout(link.organisationSlug(), link.linkToken());
		TenantContext.set(fixture.organisation().getId(), null);
		String gatewayOrderId = paymentGatewayOrderRepository.findByLinkToken(link.linkToken()).orElseThrow().getGatewayOrderId();
		TenantContext.clear();

		gateway.signatureValid = true;
		gatewayService().handleWebhook("{}", "sig", "payment.failed", gatewayOrderId, "pay_stub_4");

		TenantContext.set(fixture.organisation().getId(), null);
		assertThat(invoiceRepository.findById(fixture.invoice().getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.ISSUED);
	}

	private static class StubRazorpayGateway implements RazorpayGateway {
		// Static - JUnit 5 creates a fresh test instance (and a fresh gateway field) per
		// test method, but the H2 database persists across the whole class
		// (@Transactional(NOT_SUPPORTED) means nothing rolls back), so a per-instance
		// counter restarting at 0 in every test would let two different tests generate
		// the same gateway_order_id and collide on PaymentGatewayOrderRepository's unique
		// constraint / findByGatewayOrderId's uniqueness assumption.
		private static final AtomicInteger orderCounter = new AtomicInteger();
		private boolean signatureValid = true;

		@Override
		public String createOrder(RazorpayCredentials credentials, long amountInPaise, String currency, String receipt) {
			return "order_stub_" + orderCounter.incrementAndGet();
		}

		@Override
		public boolean verifyWebhookSignature(String rawBody, String signatureHeader, String webhookSecret) {
			return signatureValid;
		}
	}
}
