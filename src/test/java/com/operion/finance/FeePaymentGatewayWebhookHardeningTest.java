package com.operion.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.customfield.CustomFieldDefinitionRepository;
import com.operion.customfield.CustomFieldService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Webhook hardening for FeePaymentGatewayService (security findings FINANCE 1-3): concurrent and repeated
 * delivery credits once, an amount or currency that does not match the quote credits nothing, an unknown
 * gateway order credits nothing and does not throw (so Razorpay does not retry it for a day), and a re-quoted
 * checkout reuses or adds a gateway order without orphaning the earlier one. Same fixture shape and stubbing
 * approach as FeePaymentGatewayServiceTest, kept in its own class so that file stays untouched.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, FeeService.class, StudentIdGenerator.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FeePaymentGatewayWebhookHardeningTest {

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
	private PaymentGatewayAttemptRepository paymentGatewayAttemptRepository;
	@Autowired
	private PlatformTransactionManager transactionManager;
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

	private final StubRazorpayGateway gateway = new StubRazorpayGateway();

	@BeforeEach
	void setUpStudentService() {
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
			studentExitRepository, sectionRepository, gradeLevelRepository, null, null, studentIdGenerator,
			new AuditLogService(auditLogRepository, new ObjectMapper()),
			new CustomFieldService(customFieldDefinitionRepository, new ObjectMapper()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private FeePaymentGatewayService gatewayService() {
		RazorpayCredentialsProvider credentialsProvider =
				new RazorpayCredentialsProvider("test-key-id", "test-key-secret", "test-webhook-secret");
		return new FeePaymentGatewayService(paymentGatewayOrderRepository, paymentGatewayAttemptRepository, invoiceRepository,
				organisationRepository, feeService, credentialsProvider, gateway, transactionManager);
	}

	private record Fixture(Organisation organisation, Invoice invoice, String linkToken, String slug) {
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

		FeeStructureGroup feeStructureGroup = feeService.createFeeStructureGroup("Grade 7 Annual Fees 2025-26", academicYear, schoolClass);
		FeeCategory feeCategory = feeService.createCategory("TUITION", "Tuition Fee", null, FeeCategoryType.GENERAL);
		FeeStructure feeStructure = feeService.createFeeStructure(feeStructureGroup, feeCategory, new BigDecimal("5000.00"),
				List.of(new InstallmentInput(1, LocalDate.of(2025, 6, 15), new BigDecimal("5000.00"))));
		StudentFeeAssignment assignment = feeService.assignFee(enrollment, feeStructure, null, null, null);
		FeeStructureInstallment installment =
				feeStructureInstallmentRepository.findByFeeStructureIdOrderByInstallmentNumber(feeStructure.getId()).get(0);
		Invoice invoice = feeService.generateInvoice(assignment, installment);

		FeePaymentGatewayService.IssuedLink link = gatewayService().createLink(invoice.getId());
		TenantContext.clear();
		return new Fixture(organisation, invoice, link.linkToken(), link.organisationSlug());
	}

	/** Runs a public checkout (no tenant in context, like the real endpoint) and returns the gateway order id. */
	private String checkout(Fixture fixture) {
		TenantContext.clear();
		return gatewayService().initiateCheckout(fixture.slug(), fixture.linkToken()).gatewayOrderId();
	}

	private void webhook(String gatewayOrderId, String paymentId, Long paise, String currency) {
		TenantContext.clear();
		gatewayService().handleWebhook("{}", "sig", "payment.captured", gatewayOrderId, paymentId, paise, currency);
	}

	private Invoice reload(Fixture fixture) {
		TenantContext.set(fixture.organisation().getId(), null);
		return invoiceRepository.findById(fixture.invoice().getId()).orElseThrow();
	}

	private PaymentGatewayOrder reloadOrder(Fixture fixture) {
		TenantContext.set(fixture.organisation().getId(), null);
		return paymentGatewayOrderRepository.findByLinkToken(fixture.linkToken()).orElseThrow();
	}

	@Test
	void concurrentDeliveriesOfTheSamePaymentCreditExactlyOnce() throws Exception {
		Fixture fixture = setUpFixture("pgh-concurrent", "ADM-PGH-1");
		String gatewayOrderId = checkout(fixture);

		int threads = 4;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Void>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				Callable<Void> delivery = () -> {
					start.await();
					gatewayService().handleWebhook("{}", "sig", "payment.captured", gatewayOrderId, "pay_concurrent_1", 500_000L, "INR");
					return null;
				};
				results.add(pool.submit(delivery));
			}
			start.countDown();
			for (Future<Void> result : results) {
				result.get(60, TimeUnit.SECONDS);
			}
		} finally {
			pool.shutdownNow();
		}

		Invoice invoice = reload(fixture);
		assertThat(invoice.getAmountPaid()).isEqualByComparingTo("5000.00");
		assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
		PaymentGatewayOrder order = reloadOrder(fixture);
		assertThat(order.getStatus()).isEqualTo(PaymentGatewayOrderStatus.PAID);
		assertThat(order.getGatewayPaymentId()).isEqualTo("pay_concurrent_1");
	}

	@Test
	void aSecondDifferentPaymentAgainstAnAlreadyPaidLinkIsNotCreditedAndDoesNotThrow() {
		Fixture fixture = setUpFixture("pgh-second-payment", "ADM-PGH-2");
		String gatewayOrderId = checkout(fixture);

		webhook(gatewayOrderId, "pay_first", 500_000L, "INR");
		webhook(gatewayOrderId, "pay_second", 500_000L, "INR");

		assertThat(reload(fixture).getAmountPaid()).isEqualByComparingTo("5000.00");
		assertThat(reloadOrder(fixture).getGatewayPaymentId()).isEqualTo("pay_first");
	}

	@Test
	void anAmountThatDoesNotMatchTheQuoteIsNotCredited() {
		Fixture fixture = setUpFixture("pgh-amount-mismatch", "ADM-PGH-3");
		String gatewayOrderId = checkout(fixture);

		webhook(gatewayOrderId, "pay_low", 100_000L, "INR");
		webhook(gatewayOrderId, "pay_high", 900_000L, "INR");
		webhook(gatewayOrderId, "pay_missing_amount", null, "INR");

		Invoice invoice = reload(fixture);
		assertThat(invoice.getAmountPaid()).isEqualByComparingTo("0.00");
		assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
		assertThat(reloadOrder(fixture).getStatus()).isNotEqualTo(PaymentGatewayOrderStatus.PAID);
	}

	@Test
	void aCurrencyThatDoesNotMatchTheQuoteIsNotCredited() {
		Fixture fixture = setUpFixture("pgh-currency-mismatch", "ADM-PGH-4");
		String gatewayOrderId = checkout(fixture);

		webhook(gatewayOrderId, "pay_usd", 500_000L, "USD");
		webhook(gatewayOrderId, "pay_no_currency", 500_000L, null);

		assertThat(reload(fixture).getAmountPaid()).isEqualByComparingTo("0.00");
		assertThat(reloadOrder(fixture).getStatus()).isNotEqualTo(PaymentGatewayOrderStatus.PAID);
	}

	@Test
	void aMismatchedDeliveryDoesNotBlockTheCorrectOneArrivingLater() {
		Fixture fixture = setUpFixture("pgh-mismatch-then-ok", "ADM-PGH-5");
		String gatewayOrderId = checkout(fixture);

		webhook(gatewayOrderId, "pay_wrong", 1L, "INR");
		webhook(gatewayOrderId, "pay_right", 500_000L, "INR");

		assertThat(reload(fixture).getAmountPaid()).isEqualByComparingTo("5000.00");
		assertThat(reloadOrder(fixture).getGatewayPaymentId()).isEqualTo("pay_right");
	}

	@Test
	void anUnknownGatewayOrderIsLoggedAndAcknowledgedWithoutCreditingAnything() {
		Fixture fixture = setUpFixture("pgh-unknown-order", "ADM-PGH-6");
		checkout(fixture);

		// Does not throw: Razorpay would retry a non-2xx for ~24h and this can never succeed on retry
		// (and the Razorpay account is shared, so the order may legitimately belong to someone else).
		webhook("order_does_not_exist", "pay_orphan", 500_000L, "INR");

		assertThat(reload(fixture).getAmountPaid()).isEqualByComparingTo("0.00");
	}

	@Test
	void aMissingOrderOrPaymentIdIsAcknowledgedWithoutCreditingAnything() {
		Fixture fixture = setUpFixture("pgh-missing-ids", "ADM-PGH-7");
		String gatewayOrderId = checkout(fixture);

		webhook(null, "pay_x", 500_000L, "INR");
		webhook(gatewayOrderId, null, 500_000L, "INR");

		assertThat(reload(fixture).getAmountPaid()).isEqualByComparingTo("0.00");
	}

	@Test
	void repeatedCheckoutAtTheSameOutstandingReusesTheRazorpayOrder() {
		Fixture fixture = setUpFixture("pgh-reuse", "ADM-PGH-8");

		String first = checkout(fixture);
		String second = checkout(fixture);

		assertThat(second).isEqualTo(first);
		assertThat(gateway.createdOrders.get()).isEqualTo(1);
		TenantContext.set(fixture.organisation().getId(), null);
		assertThat(paymentGatewayAttemptRepository.findAll()).hasSize(1);
	}

	@Test
	void aReQuotedCheckoutKeepsTheEarlierGatewayOrderResolvable() {
		Fixture fixture = setUpFixture("pgh-requote", "ADM-PGH-9");
		String firstOrderId = checkout(fixture);

		// 1000 paid by cash after the first quote: the outstanding is now 4000 so the next checkout re-quotes.
		TenantContext.set(fixture.organisation().getId(), null);
		Invoice invoice = invoiceRepository.findById(fixture.invoice().getId()).orElseThrow();
		feeService.recordPayment(invoice.getAcademicYear(), new BigDecimal("1000.00"), PaymentMethod.CASH, LocalDate.of(2025, 6, 20), null,
				List.of(new FeeService.AllocationInput(invoice.getId(), new BigDecimal("1000.00"))));

		String secondOrderId = checkout(fixture);
		assertThat(secondOrderId).isNotEqualTo(firstOrderId);
		assertThat(reloadOrder(fixture).getGatewayOrderId()).isEqualTo(secondOrderId);
		assertThat(paymentGatewayAttemptRepository.findAll()).hasSize(2);

		// The parent paid against the FIRST (stale, 5000) order: it is still found, but it now exceeds what is
		// owed, so it is not credited (and does not throw).
		webhook(firstOrderId, "pay_stale", 500_000L, "INR");
		assertThat(reload(fixture).getAmountPaid()).isEqualByComparingTo("1000.00");

		// Paying the current order (4000) credits exactly that amount.
		webhook(secondOrderId, "pay_current", 400_000L, "INR");
		Invoice reloaded = reload(fixture);
		assertThat(reloaded.getAmountPaid()).isEqualByComparingTo("5000.00");
		assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.PAID);
	}

	@Test
	void anEarlierOrderStillCreditsItsOwnQuotedAmountWhenThatIsStillOwed() {
		Fixture fixture = setUpFixture("pgh-earlier-order", "ADM-PGH-10");
		String firstOrderId = checkout(fixture);

		TenantContext.set(fixture.organisation().getId(), null);
		Invoice invoice = invoiceRepository.findById(fixture.invoice().getId()).orElseThrow();
		// Raise the outstanding so the re-quote differs from the first order, in the other direction.
		feeService.recordAdjustment(invoice, new BigDecimal("500.00"), "late fee", 1L, LocalDate.of(2025, 6, 21), null, null);
		String secondOrderId = checkout(fixture);
		assertThat(secondOrderId).isNotEqualTo(firstOrderId);

		// Paid against the first order's 5000 quote: matches that attempt's amount and is still owed (5500).
		webhook(firstOrderId, "pay_first_quote", 500_000L, "INR");
		assertThat(reload(fixture).getAmountPaid()).isEqualByComparingTo("5000.00");
	}

	private static class StubRazorpayGateway implements RazorpayGateway {
		private static final AtomicInteger orderCounter = new AtomicInteger();
		private final AtomicInteger createdOrders = new AtomicInteger();

		@Override
		public String createOrder(RazorpayCredentials credentials, long amountInPaise, String currency, String receipt) {
			createdOrders.incrementAndGet();
			return "order_hardening_" + orderCounter.incrementAndGet();
		}

		@Override
		public boolean verifyWebhookSignature(String rawBody, String signatureHeader, String webhookSecret) {
			return true;
		}
	}
}
