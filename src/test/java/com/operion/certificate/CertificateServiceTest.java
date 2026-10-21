package com.operion.certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.certificate.CertificateService.PreparedCertificate;
import com.operion.certificate.CertificateService.VerificationResult;
import com.operion.common.InvalidRequestException;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.document.DocumentBrandingService;
import com.operion.document.IssuedDocumentRepository;
import com.operion.document.pdf.DocumentHtmlRenderer;
import com.operion.document.pdf.OpenHtmlToPdfRenderer;
import com.operion.finance.FeeCategory;
import com.operion.finance.FeeCategoryType;
import com.operion.finance.FeeService;
import com.operion.finance.FeeService.InstallmentInput;
import com.operion.finance.FeeStructure;
import com.operion.finance.FeeStructureGroup;
import com.operion.finance.FeeStructureInstallmentRepository;
import com.operion.finance.StudentFeeAssignment;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.library.Book;
import com.operion.library.BookCopy;
import com.operion.library.BookCopyRepository;
import com.operion.library.BookRepository;
import com.operion.library.BorrowRecord;
import com.operion.library.BorrowRecordRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationBrandingRepository;
import com.operion.organisation.OrganisationConfigurationRepository;
import com.operion.organisation.OrganisationRepository;
import com.operion.parent.StudentGuardianRepository;
import com.operion.storage.AssetStorageService;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentExit;
import com.operion.student.StudentExitRepository;
import com.operion.student.StudentExitType;
import com.operion.student.StudentRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Certificates end to end at the service level: a TC needs an exit, approval by someone else and dues clearance
 * (or a recorded override); issue numbers from the type's counter and freezes a snapshot; reprints are DUPLICATE;
 * the public verification shows minimal fields and follows cancellation. A bonafide needs none of that, and only
 * guardian-requestable types can be raised from the portal.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, FeeService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CertificateServiceTest {

	private static final long CLERK = 101L;
	private static final long PRINCIPAL = 102L;

	@Autowired private FeeService feeService;
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
	@Autowired private StudentEnrollmentRepository enrollmentRepository;
	@Autowired private StudentExitRepository exitRepository;
	@Autowired private StudentGuardianRepository studentGuardianRepository;
	@Autowired private FeeStructureInstallmentRepository installmentRepository;
	@Autowired private BookRepository bookRepository;
	@Autowired private BookCopyRepository bookCopyRepository;
	@Autowired private BorrowRecordRepository borrowRecordRepository;
	@Autowired private IssuedDocumentRepository issuedDocumentRepository;
	@Autowired private CertificateRequestRepository requestRepository;
	@Autowired private CertificateTypeRepository typeRepository;
	@Autowired private CertificateTypePresetRepository presetRepository;
	@Autowired private PlatformTransactionManager transactionManager;

	private CertificateService service;
	private CertificateTypeService typeService;
	private Organisation organisation;

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
		DocumentBrandingService branding = new DocumentBrandingService(brandingRepository, configurationRepository, organisationRepository, noAssets);
		service = new CertificateService(requestRepository, typeRepository, studentRepository, exitRepository, issuedDocumentRepository,
				organisationRepository, new CertificateMergeFields(enrollmentRepository, exitRepository, studentGuardianRepository, requestRepository),
				branding, new DocumentHtmlRenderer(), new OpenHtmlToPdfRenderer(), new ObjectMapper(), "https://school.example.test/");
		typeService = new CertificateTypeService(typeRepository, presetRepository);
		if (presetRepository.findByCode("TC").isEmpty()) {
			presetRepository.save(new CertificateTypePreset("TC", "Transfer Certificate", "TRANSFER CERTIFICATE",
					"Certified that the above particulars are as recorded at {{school.name}}.",
					"Name of the student | {{student.name}}\nDate of birth | {{student.dateOfBirth}}\nDate of birth (in words) | {{student.dateOfBirthInWords}}"
							+ "\nClass last studied | {{class.current}}\nReason for leaving | {{exit.reason}}\nGeneral conduct | {{manual.conduct}}"
							+ "\nParent / guardian | {{guardian.name}}",
					"conduct | General conduct", "TC-{YYYY}-{SEQ:4}", true, true, true, true, false));
			presetRepository.save(new CertificateTypePreset("BONAFIDE", "Bonafide Certificate", "BONAFIDE CERTIFICATE",
					"This is to certify that {{student.name}} is a bonafide student of {{school.name}}, studying in {{class.current}}.\n\n"
							+ "Issued for the purpose of {{purpose}}.",
					null, null, "BC-{YYYY}-{SEQ:4}", false, false, false, false, true));
		}
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private <T> T tx(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}

	private void as(long actorId) {
		TenantContext.set(organisation.getId(), actorId);
	}

	private record Fixture(Student student, StudentFeeAssignment feeAssignment, CertificateType tc, CertificateType bonafide) {
	}

	private Fixture fixture(String slug) {
		organisation = organisationRepository.save(new Organisation("Sunrise Academy", "Sunrise Trust", slug));
		as(CLERK);
		AcademicYear year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		Person person = new Person("Meera", "Nair");
		person.setDateOfBirth(LocalDate.of(2015, 3, 5));
		personRepository.save(person);
		Student student = studentRepository.save(new Student(person, "STU-501", "ADM-501", LocalDate.of(2020, 6, 1), null, null, null, null,
				null, null, null, null, null, null, null, null));
		StudentEnrollment enrollment = enrollmentRepository.save(new StudentEnrollment(student, year, section, 1, LocalDate.of(2025, 6, 1)));

		FeeStructureGroup group = feeService.createFeeStructureGroup("Grade 5 Fees", year, schoolClass);
		FeeCategory tuition = feeService.createCategory("TUITION", "Tuition Fee", null, FeeCategoryType.GENERAL);
		FeeStructure structure = feeService.createFeeStructure(group, tuition, new BigDecimal("5000.00"),
				List.of(new InstallmentInput(1, LocalDate.of(2025, 6, 15), new BigDecimal("5000.00"))));
		StudentFeeAssignment assignment = feeService.assignFee(enrollment, structure, BigDecimal.ZERO, null, null);
		feeService.generateInvoice(assignment, installmentRepository.findByFeeStructureIdOrderByInstallmentNumber(structure.getId()).get(0));

		CertificateType tc = tx(() -> typeService.create("TC", "Transfer Certificate"));
		CertificateType bonafide = tx(() -> typeService.create("BONAFIDE", "Bonafide Certificate"));
		return new Fixture(student, assignment, tc, bonafide);
	}

	private static String text(byte[] pdf) throws IOException {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(document);
		}
	}

	@Test
	void transferCertificateNeedsExitApprovalByAnotherPersonAndClearanceOrARecordedOverride() throws IOException {
		Fixture f = fixture("cert-tc-" + System.nanoTime());
		Long studentId = f.student().getId();

		assertThatThrownBy(() -> tx(() -> service.request(studentId, f.tc().getId(), null, null, CertificateRequestChannel.STAFF)))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("exit");

		exitRepository.save(new StudentExit(f.student(), StudentExitType.TRANSFER, LocalDate.of(2026, 3, 31), "Family relocating", "Valley School", CLERK));
		CertificateRequest request = tx(() -> service.request(studentId, f.tc().getId(), null, Map.of("conduct", "Good", "unknown", "dropped"),
				CertificateRequestChannel.STAFF));
		assertThat(request.getStatus()).isEqualTo(CertificateRequestStatus.PENDING_APPROVAL);
		assertThat(service.readManual(request)).containsExactly(Map.entry("conduct", "Good"));

		assertThatThrownBy(() -> tx(() -> service.approve(request.getId()))).isInstanceOf(AuthorizationDeniedException.class);
		as(PRINCIPAL);
		assertThat(tx(() -> service.approve(request.getId())).getStatus()).isEqualTo(CertificateRequestStatus.APPROVED);

		as(CLERK);
		assertThat(service.clearance(request.getId()).clear()).isFalse();
		assertThatThrownBy(() -> tx(() -> service.issue(request.getId(), null)))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Fees");

		CertificateRequest issued = tx(() -> service.issue(request.getId(), "Fees waived by management"));
		int year = LocalDate.now().getYear();
		assertThat(issued.getStatus()).isEqualTo(CertificateRequestStatus.ISSUED);
		assertThat(issued.getCertificateNumber()).isEqualTo("TC-" + year + "-0001");
		assertThat(issued.getClearanceOverrideReason()).isEqualTo("Fees waived by management");
		assertThat(issued.getClearanceOverriddenBy()).isEqualTo(CLERK);
		assertThat(issued.getVerificationToken()).hasSizeGreaterThanOrEqualTo(30);

		PreparedCertificate first = tx(() -> service.prepare(request.getId(), true));
		assertThat(first.duplicate()).isFalse();
		assertThat(first.verifyUrl()).startsWith("https://school.example.test/verify/" + organisation.getSlug() + "/");
		String pdf = text(service.renderPdf(first));
		assertThat(pdf).contains("TRANSFER CERTIFICATE", "TC-" + year + "-0001", "Meera Nair", "5 March 2015", "Fifth March Two Thousand Fifteen",
				"Grade 5 - A", "Family relocating", "Good", "Sunrise Academy", "________");
		assertThat(tx(() -> service.prepare(request.getId(), true)).duplicate()).isTrue();
		assertThat(tx(() -> service.prepare(request.getId(), false)).duplicate()).isFalse();

		assertThatThrownBy(() -> tx(() -> service.request(studentId, f.tc().getId(), null, null, CertificateRequestChannel.STAFF)))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("already");

		VerificationResult verified = service.verify(issued.getVerificationToken()).orElseThrow();
		assertThat(verified).isEqualTo(new VerificationResult("CERTIFICATE", "Transfer Certificate", "TC-" + year + "-0001", LocalDate.now(),
				"Sunrise Academy", "Meera Nair", "ISSUED"));
		assertThat(service.verify("not-a-real-token")).isEmpty();

		tx(() -> service.cancel(request.getId(), "Issued with wrong date"));
		assertThat(service.verify(issued.getVerificationToken()).orElseThrow().status()).isEqualTo("CANCELLED");
		PreparedCertificate cancelled = tx(() -> service.prepare(request.getId(), true));
		assertThat(cancelled.cancelled()).isTrue();
		assertThat(text(service.renderPdf(cancelled))).contains("CANCELLED");

		// A cancelled TC frees the once-per-student rule; the reissue takes the next number.
		CertificateRequest reissue = tx(() -> service.request(studentId, f.tc().getId(), null, null, CertificateRequestChannel.STAFF));
		as(PRINCIPAL);
		tx(() -> service.approve(reissue.getId()));
		as(CLERK);
		assertThat(tx(() -> service.issue(reissue.getId(), "Still waived")).getCertificateNumber()).isEqualTo("TC-" + year + "-0002");
	}

	@Test
	void clearanceListsFeesLibraryAndTransportSeparately() {
		Fixture f = fixture("cert-clear-" + System.nanoTime());
		Book book = bookRepository.save(new Book("978-0", "Wings of Fire", "A. P. J. Abdul Kalam", null, null, null));
		BookCopy copy = bookCopyRepository.save(new BookCopy(book, campusRepository.findAll().get(0), "ACC-1", null, null));
		borrowRecordRepository.save(new BorrowRecord(copy, f.student().getPerson(), LocalDate.now().minusDays(3), LocalDate.now().plusDays(7)));

		CertificateService.Clearance clearance = service.clearance(f.student());

		assertThat(clearance.clear()).isFalse();
		assertThat(clearance.items()).extracting(CertificateService.ClearanceItem::code, CertificateService.ClearanceItem::clear)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("FEES", false), org.assertj.core.groups.Tuple.tuple("LIBRARY_LOANS", false),
						org.assertj.core.groups.Tuple.tuple("LIBRARY_FINES", true), org.assertj.core.groups.Tuple.tuple("TRANSPORT", true));
		assertThat(clearance.items().get(0).detail()).contains("5,000.00");
	}

	@Test
	void bonafideIsIssuedWithoutApprovalOrClearanceAndOnlyGuardianRequestableTypesComeFromThePortal() throws IOException {
		Fixture f = fixture("cert-bona-" + System.nanoTime());
		Long studentId = f.student().getId();

		CertificateRequest request = tx(() -> service.request(studentId, f.bonafide().getId(), "Passport application", null,
				CertificateRequestChannel.PORTAL));
		assertThat(request.getStatus()).isEqualTo(CertificateRequestStatus.APPROVED);
		assertThatThrownBy(() -> tx(() -> service.request(studentId, f.bonafide().getId(), "Bank", null, CertificateRequestChannel.STAFF)))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("open");
		exitRepository.save(new StudentExit(f.student(), StudentExitType.TRANSFER, LocalDate.of(2026, 3, 31), null, null, CLERK));
		assertThatThrownBy(() -> tx(() -> service.request(studentId, f.tc().getId(), null, null, CertificateRequestChannel.PORTAL)))
				.isInstanceOf(AuthorizationDeniedException.class);

		CertificateRequest issued = tx(() -> service.issue(request.getId(), null));
		assertThat(issued.getCertificateNumber()).isEqualTo("BC-" + LocalDate.now().getYear() + "-0001");
		assertThat(issued.getClearanceOverrideReason()).isNull();

		String pdf = text(service.renderPdf(tx(() -> service.prepare(request.getId(), false))));
		assertThat(pdf).contains("BONAFIDE CERTIFICATE", "Passport application", "Grade 5 - A");
	}

	@Test
	void rejectNeedsAReasonAndEndsTheRequest() {
		Fixture f = fixture("cert-reject-" + System.nanoTime());
		exitRepository.save(new StudentExit(f.student(), StudentExitType.WITHDRAWAL, LocalDate.of(2026, 3, 31), null, null, CLERK));
		CertificateRequest request = tx(() -> service.request(f.student().getId(), f.tc().getId(), null, null, CertificateRequestChannel.STAFF));

		as(PRINCIPAL);
		assertThatThrownBy(() -> tx(() -> service.reject(request.getId(), " "))).isInstanceOf(InvalidRequestException.class);
		CertificateRequest rejected = tx(() -> service.reject(request.getId(), "Exit date is wrong"));

		assertThat(rejected.getStatus()).isEqualTo(CertificateRequestStatus.REJECTED);
		assertThatThrownBy(() -> tx(() -> service.issue(request.getId(), "x"))).isInstanceOf(IllegalStateException.class);
		// A rejected request does not block a fresh one.
		assertThat(tx(() -> service.request(f.student().getId(), f.tc().getId(), null, null, CertificateRequestChannel.STAFF)).getStatus())
				.isEqualTo(CertificateRequestStatus.PENDING_APPROVAL);
	}

	@Test
	void typeRulesAreValidatedAndCustomCodesGetGenericWording() {
		fixture("cert-types-" + System.nanoTime());
		CertificateType custom = tx(() -> typeService.create("sports", "Sports Participation"));
		assertThat(custom.getCode()).isEqualTo("SPORTS");
		assertThat(custom.getBodyTemplate()).contains("{{student.name}}");
		assertThatThrownBy(() -> tx(() -> typeService.create("SPORTS", "Again"))).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> tx(() -> typeService.update(custom.getId(), new CertificateTypeService.TypeDetails("Sports", "SPORTS",
				"Body", null, null, "SP-NO-SEQ", "A4", false, false, false, false, false, true))))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("{SEQ}");
	}

	@Test
	void substitutionNeverEvaluatesExpressionsAndBlanksUnknownTokens() {
		String out = CertificateMergeFields.substitute("A ${T(java.lang.Runtime)} [[${x}]] {{student.name}} {{nope}}",
				Map.of("student.name", "Meera $1 \\ Nair"));
		assertThat(out).isEqualTo("A ${T(java.lang.Runtime)} [[${x}]] Meera $1 \\ Nair ________");
	}
}
