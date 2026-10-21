package com.operion.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.Role;
import com.operion.authorization.RoleRepository;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.document.DocumentBrandingService;
import com.operion.document.DocumentTemplateRepository;
import com.operion.document.IssuedDocument;
import com.operion.document.IssuedDocumentKind;
import com.operion.document.IssuedDocumentRepository;
import com.operion.document.IssuedDocumentStatus;
import com.operion.document.pdf.DocumentHtmlRenderer;
import com.operion.document.pdf.OpenHtmlToPdfRenderer;
import com.operion.finance.FeeService.AllocationInput;
import com.operion.finance.FeeService.InstallmentInput;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationBrandingRepository;
import com.operion.organisation.OrganisationConfigurationRepository;
import com.operion.organisation.OrganisationRepository;
import com.operion.parent.Guardian;
import com.operion.parent.GuardianRelationshipType;
import com.operion.parent.GuardianRepository;
import com.operion.parent.ParentService;
import com.operion.parent.StudentGuardianRepository;
import com.operion.portal.GuardianPaymentService;
import com.operion.portal.GuardianPaymentSummary;
import com.operion.portal.GuardianPortalService;
import com.operion.storage.AssetStorageService;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Receipts: the first office print freezes a snapshot, later office prints are DUPLICATE,
 * guardian downloads never count, a bounce marks the receipt CANCELLED, and the PDF carries
 * the frozen values. Guardians only get receipts that cover nothing but their own children.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, FeeService.class, ParentService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReceiptServiceTest {

	@Autowired private FeeService feeService;
	@Autowired private ParentService parentService;
	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private OrganisationBrandingRepository brandingRepository;
	@Autowired private OrganisationConfigurationRepository configurationRepository;
	@Autowired private CampusRepository campusRepository;
	@Autowired private AcademicYearRepository academicYearRepository;
	@Autowired private GradeLevelRepository gradeLevelRepository;
	@Autowired private SchoolClassRepository schoolClassRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private StudentRepository studentRepository;
	@Autowired private StudentEnrollmentRepository studentEnrollmentRepository;
	@Autowired private FeeStructureInstallmentRepository feeStructureInstallmentRepository;
	@Autowired private PaymentRepository paymentRepository;
	@Autowired private PaymentAllocationRepository paymentAllocationRepository;
	@Autowired private InvoiceRepository invoiceRepository;
	@Autowired private IssuedDocumentRepository issuedDocumentRepository;
	@Autowired private DocumentTemplateRepository documentTemplateRepository;
	@Autowired private OrganisationMembershipRepository membershipRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private RoleRepository roleRepository;
	@Autowired private GuardianRepository guardianRepository;
	@Autowired private StudentGuardianRepository studentGuardianRepository;

	@Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

	private ReceiptService receiptService;

	/** The service is built by hand, so its @Transactional isn't proxied - the row lock needs a transaction. */
	private ReceiptService.PreparedReceipt prepare(Long paymentId, boolean officePrint) {
		return new org.springframework.transaction.support.TransactionTemplate(transactionManager)
				.execute(status -> receiptService.prepare(paymentId, officePrint));
	}

	private final AssetStorageService noAssets = new AssetStorageService() {
		@Override
		public String store(MultipartFile file) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String resolveUrl(String reference) {
			return reference;
		}

		@Override
		public boolean exists(String reference) {
			return false;
		}

		@Override
		public Optional<byte[]> read(String reference) {
			return Optional.empty();
		}
	};

	@BeforeEach
	void setUp() {
		DocumentBrandingService branding =
				new DocumentBrandingService(brandingRepository, configurationRepository, organisationRepository, noAssets);
		receiptService = new ReceiptService(paymentRepository, paymentAllocationRepository, invoiceRepository, issuedDocumentRepository,
				documentTemplateRepository, membershipRepository, branding, new DocumentHtmlRenderer(), new OpenHtmlToPdfRenderer(),
				new ObjectMapper());
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(AcademicYear year, Campus campus, Student ownChild, Student sibling, Invoice ownInvoice1, Invoice ownInvoice2,
			Invoice siblingInvoice) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Sunrise Academy", "Sunrise Trust", slug));
		TenantContext.set(organisation.getId(), null);
		AcademicYear year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, null));

		FeeStructureGroup group = feeService.createFeeStructureGroup("Grade 5 Fees", year, schoolClass);
		FeeCategory tuition = feeService.createCategory("TUITION", "Tuition Fee", null, FeeCategoryType.GENERAL);
		FeeStructure structure = feeService.createFeeStructure(group, tuition, new BigDecimal("10000.00"),
				List.of(new InstallmentInput(1, LocalDate.of(2025, 6, 15), new BigDecimal("5000.00")),
						new InstallmentInput(2, LocalDate.of(2025, 10, 15), new BigDecimal("5000.00"))));
		List<FeeStructureInstallment> installments = feeStructureInstallmentRepository.findByFeeStructureIdOrderByInstallmentNumber(structure.getId());

		Student ownChild = admit("Meera", "ADM-501", "STU-501", year, section);
		Student sibling = admit("Arjun", "ADM-502", "STU-502", year, section);
		StudentFeeAssignment ownAssignment = feeService.assignFee(enrollmentOf(ownChild), structure, BigDecimal.ZERO, null, null);
		StudentFeeAssignment siblingAssignment = feeService.assignFee(enrollmentOf(sibling), structure, BigDecimal.ZERO, null, null);
		return new Fixture(year, campus, ownChild, sibling, feeService.generateInvoice(ownAssignment, installments.get(0)),
				feeService.generateInvoice(ownAssignment, installments.get(1)), feeService.generateInvoice(siblingAssignment, installments.get(0)));
	}

	private Student admit(String firstName, String admissionNumber, String studentId, AcademicYear year, Section section) {
		Person person = personRepository.save(new Person(firstName, "Nair"));
		Student student = studentRepository.save(new Student(person, studentId, admissionNumber, LocalDate.of(2025, 5, 1), null, null,
				null, null, null, null, null, null, null, null, null, null));
		studentEnrollmentRepository.save(new StudentEnrollment(student, year, section, 1, LocalDate.of(2025, 6, 1)));
		return student;
	}

	private StudentEnrollment enrollmentOf(Student student) {
		return studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId()).orElseThrow();
	}

	private Payment pay(Fixture fixture, String amount, AllocationInput... allocations) {
		return feeService.recordPayment(fixture.year(), new BigDecimal(amount), PaymentMethod.UPI, LocalDate.of(2025, 6, 10), null,
				List.of(allocations), "UTR123456", null);
	}

	private static String text(byte[] pdf) throws Exception {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(document);
		}
	}

	@Test
	void firstOfficePrintIsOriginalLaterOnesAreDuplicatesAndGuardianCopiesNeverCount() throws Exception {
		Fixture fixture = setUpFixture("receipt-prints");
		Payment payment = pay(fixture, "6500.00", new AllocationInput(fixture.ownInvoice1().getId(), new BigDecimal("5000.00")),
				new AllocationInput(fixture.ownInvoice2().getId(), new BigDecimal("1500.00")));

		ReceiptService.PreparedReceipt first = prepare(payment.getId(), true);
		assertThat(first.duplicate()).isFalse();
		assertThat(first.cancelled()).isFalse();
		ReceiptService.ReceiptSnapshot snapshot = first.snapshot();
		assertThat(snapshot.receiptNumber()).isEqualTo(payment.getReceiptNumber());
		assertThat(snapshot.school().schoolName()).isEqualTo("Sunrise Academy");
		assertThat(snapshot.lines()).hasSize(2);
		assertThat(snapshot.lines().get(0).feeHead()).isEqualTo("Tuition Fee - Installment 1");
		assertThat(snapshot.students()).singleElement().satisfies(s -> {
			assertThat(s.name()).isEqualTo("Meera Nair");
			assertThat(s.classSection()).isEqualTo("Grade 5 - A");
		});
		assertThat(snapshot.amountText()).isEqualTo("₹ 6,500.00");
		assertThat(snapshot.amountInWords()).isEqualTo("Rupees Six Thousand Five Hundred Only");
		assertThat(snapshot.outstandingText()).isEqualTo("₹ 3,500.00");

		String original = text(receiptService.renderPdf(first));
		assertThat(original).contains("FEE RECEIPT", payment.getReceiptNumber(), "Meera Nair", "₹ 6,500.00", "UTR123456",
				"Rupees Six Thousand Five Hundred Only");
		assertThat(original).doesNotContain("DUPLICATE");

		ReceiptService.PreparedReceipt guardianCopy = prepare(payment.getId(), false);
		assertThat(guardianCopy.duplicate()).isFalse();

		ReceiptService.PreparedReceipt reprint = prepare(payment.getId(), true);
		assertThat(reprint.duplicate()).isTrue();
		assertThat(reprint.printNumber()).isEqualTo(2);
		assertThat(reprint.snapshot()).isEqualTo(snapshot);
		assertThat(text(receiptService.renderPdf(reprint))).contains("DUPLICATE COPY 2");

		List<IssuedDocument> register = issuedDocumentRepository.findAll().stream()
				.filter(d -> d.getDocumentKind() == IssuedDocumentKind.RECEIPT && d.getSubjectId().equals(payment.getId()))
				.toList();
		assertThat(register).singleElement().satisfies(d -> {
			assertThat(d.getPrintCount()).isEqualTo(2);
			assertThat(d.getDocumentNumber()).isEqualTo(payment.getReceiptNumber());
			assertThat(d.getContentHash()).hasSize(64);
		});
	}

	@Test
	void bouncedPaymentReceiptIsMarkedCancelled() throws Exception {
		Fixture fixture = setUpFixture("receipt-bounce");
		Payment payment = pay(fixture, "5000.00", new AllocationInput(fixture.ownInvoice1().getId(), new BigDecimal("5000.00")));
		prepare(payment.getId(), true);

		feeService.bouncePayment(paymentRepository.findById(payment.getId()).orElseThrow());
		ReceiptService.PreparedReceipt afterBounce = prepare(payment.getId(), true);

		assertThat(afterBounce.cancelled()).isTrue();
		assertThat(text(receiptService.renderPdf(afterBounce))).contains("CANCELLED");
		assertThat(issuedDocumentRepository.findById(issuedDocumentRepository.findAll().get(0).getId()).orElseThrow().getStatus())
				.isEqualTo(IssuedDocumentStatus.CANCELLED);
	}

	@Test
	void guardianSeesOnlyReceiptsCoveringNothingButTheirOwnChildren() {
		Fixture fixture = setUpFixture("receipt-guardian");
		Payment ownOnly = pay(fixture, "5000.00", new AllocationInput(fixture.ownInvoice1().getId(), new BigDecimal("5000.00")));
		Payment withSibling = pay(fixture, "2000.00", new AllocationInput(fixture.ownInvoice2().getId(), new BigDecimal("1000.00")),
				new AllocationInput(fixture.siblingInvoice().getId(), new BigDecimal("1000.00")));

		Person guardianPerson = personRepository.save(new Person("Vikram", "Nair"));
		Guardian guardian = parentService.getOrCreateGuardian(guardianPerson, "Engineer");
		parentService.linkGuardian(fixture.ownChild(), guardian, GuardianRelationshipType.FATHER, true, true, true, true, 1);
		User user = userRepository.save(new User("guardian-receipt@example.com", null, "hash"));
		Role role = roleRepository.save(new Role("Guardian", "portal access", false, true, true));
		membershipRepository.save(new OrganisationMembership(user, guardianPerson, role, fixture.campus()));

		GuardianPortalService portal = new GuardianPortalService(membershipRepository, guardianRepository, studentGuardianRepository,
				studentEnrollmentRepository, null, null, invoiceRepository, null, null);
		GuardianPaymentService payments = new GuardianPaymentService(portal, paymentAllocationRepository);
		Long ownChildId = fixture.ownChild().getId();

		assertThat(payments.paymentsFor(user.getId(), ownChildId)).extracting(GuardianPaymentSummary::paymentId)
				.containsExactly(ownOnly.getId());
		payments.requireReceiptAccess(user.getId(), ownChildId, ownOnly.getId());

		assertThatThrownBy(() -> payments.requireReceiptAccess(user.getId(), ownChildId, withSibling.getId()))
				.isInstanceOf(AuthorizationDeniedException.class);
		assertThatThrownBy(() -> payments.paymentsFor(user.getId(), fixture.sibling().getId()))
				.isInstanceOf(AuthorizationDeniedException.class);
	}
}
