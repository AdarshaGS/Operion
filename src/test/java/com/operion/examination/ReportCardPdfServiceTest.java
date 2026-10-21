package com.operion.examination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.InvalidRequestException;
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
import com.operion.examination.ExaminationService.BandInput;
import com.operion.examination.ExaminationService.MarkInput;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationBrandingRepository;
import com.operion.organisation.OrganisationConfigurationRepository;
import com.operion.organisation.OrganisationRepository;
import com.operion.storage.AssetStorageService;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Report-card PDFs: the PDF carries the published values and subject marks; the first print
 * freezes them, so a later marks correction doesn't change a reprint of the same card but
 * marks it PROVISIONAL, and a superseded card prints SUPERSEDED. The section print puts every
 * published card of the section in one PDF, one page each.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReportCardPdfServiceTest {

	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private OrganisationBrandingRepository brandingRepository;
	@Autowired private OrganisationConfigurationRepository configurationRepository;
	@Autowired private CampusRepository campusRepository;
	@Autowired private AcademicYearRepository academicYearRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private GradeLevelRepository gradeLevelRepository;
	@Autowired private SchoolClassRepository schoolClassRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private SubjectRepository subjectRepository;
	@Autowired private StudentRepository studentRepository;
	@Autowired private StudentEnrollmentRepository studentEnrollmentRepository;
	@Autowired private ExamRepository examRepository;
	@Autowired private ExamScheduleRepository examScheduleRepository;
	@Autowired private GradingScaleRepository gradingScaleRepository;
	@Autowired private GradingScaleBandRepository gradingScaleBandRepository;
	@Autowired private MarksEntryRepository marksEntryRepository;
	@Autowired private MarksEntryRegisterRepository marksEntryRegisterRepository;
	@Autowired private ReportCardRepository reportCardRepository;
	@Autowired private ExaminationSettingsRepository examinationSettingsRepository;
	@Autowired private AuditLogRepository auditLogRepository;
	@Autowired private IssuedDocumentRepository issuedDocumentRepository;
	@Autowired private DocumentTemplateRepository documentTemplateRepository;
	@Autowired private PlatformTransactionManager transactionManager;

	private ExaminationService examinationService;
	private ReportCardPdfService pdfService;

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
		examinationService = new ExaminationService(examRepository, examScheduleRepository, gradingScaleRepository,
				gradingScaleBandRepository, marksEntryRepository, marksEntryRegisterRepository, reportCardRepository,
				examinationSettingsRepository, new AuditLogService(auditLogRepository, new ObjectMapper()));
		DocumentBrandingService branding =
				new DocumentBrandingService(brandingRepository, configurationRepository, organisationRepository, noAssets);
		pdfService = new ReportCardPdfService(reportCardRepository, examScheduleRepository, marksEntryRepository, issuedDocumentRepository,
				documentTemplateRepository, branding, new DocumentHtmlRenderer(), new OpenHtmlToPdfRenderer(), new ObjectMapper());
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(Exam exam, Section section, ExamSchedule maths, ExamSchedule science, GradingScale scale) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Sunrise Academy", "Sunrise Trust", slug));
		TenantContext.set(organisation.getId(), null);
		AcademicYear year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		Subject maths = subjectRepository.save(new Subject("Mathematics", "MATH-" + slug));
		Subject science = subjectRepository.save(new Subject("Science", "SCI-" + slug));
		Exam exam = examinationService.createExam(year, "Half Yearly", ExamType.MID_TERM);
		ExamSchedule mathsSchedule = examinationService.addSchedule(exam, schoolClass, maths, LocalDate.of(2025, 9, 1), 100.0, 35.0);
		ExamSchedule scienceSchedule = examinationService.addSchedule(exam, schoolClass, science, LocalDate.of(2025, 9, 3), 100.0, 35.0);
		GradingScale scale = examinationService.createGradingScale("Standard", true, List.of(
				new BandInput("A+", 90.0, "Excellent"), new BandInput("A", 80.0, "Very good"), new BandInput("F", 0.0, "Fail")));
		return new Fixture(exam, section, mathsSchedule, scienceSchedule, scale);
	}

	private StudentEnrollment enrol(Fixture fixture, String firstName, String admissionNumber, int roll) {
		Person person = personRepository.save(new Person(firstName, "Nair"));
		Student student = studentRepository.save(new Student(person, "STU-" + admissionNumber, admissionNumber, LocalDate.of(2025, 5, 1),
				null, null, null, null, null, null, null, null, null, null, null, null));
		return studentEnrollmentRepository.save(new StudentEnrollment(student, fixture.exam().getAcademicYear(), fixture.section(), roll,
				LocalDate.of(2025, 6, 1)));
	}

	private void approveAll(Fixture fixture) {
		for (ExamSchedule schedule : List.of(fixture.maths(), fixture.science())) {
			examinationService.submitMarksRegister(schedule);
			examinationService.approveMarksRegister(schedule);
		}
	}

	private ReportCardPdfService.PreparedReportCards prepare(Long reportCardId, boolean officePrint) {
		return new TransactionTemplate(transactionManager).execute(status -> pdfService.prepare(reportCardId, officePrint));
	}

	private ReportCardPdfService.PreparedReportCards prepareSection(Fixture fixture) {
		return new TransactionTemplate(transactionManager)
				.execute(status -> pdfService.prepareSection(fixture.exam().getId(), fixture.section().getId()));
	}

	private static String pdfText(byte[] pdf) throws Exception {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(document);
		}
	}

	private static int pageCount(byte[] pdf) throws Exception {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			return document.getNumberOfPages();
		}
	}

	@Test
	void pdfCarriesStudentSubjectMarksAndTotals() throws Exception {
		Fixture fixture = setUpFixture("rc-pdf-content");
		StudentEnrollment meera = enrol(fixture, "Meera", "ADM-601", 1);
		examinationService.enterMarks(fixture.maths(), List.of(new MarkInput(meera, 90.0, false, null)));
		examinationService.enterMarks(fixture.science(), List.of(new MarkInput(meera, 85.0, false, null)));
		approveAll(fixture);
		ReportCard card = examinationService.publishReportCard(fixture.exam(), meera, fixture.scale());

		var prepared = prepare(card.getId(), true);
		String text = pdfText(pdfService.renderPdf(prepared));

		assertThat(text).contains("REPORT CARD", "Sunrise Academy", "Meera Nair", "ADM-601", "Half Yearly", "2025-2026",
				"Mathematics", "Science", "90.0", "85.0", "175.0", "87.50%", "PASS");
		assertThat(text).doesNotContain("PROVISIONAL", "SUPERSEDED");
		assertThat(prepared.fileName()).isEqualTo("report-card-ADM-601-Half-Yearly");

		IssuedDocument issued = issuedDocumentRepository
				.findFirstByDocumentKindAndSubjectTypeAndSubjectIdOrderByIdDesc(IssuedDocumentKind.REPORT_CARD, "REPORT_CARD", card.getId())
				.orElseThrow();
		assertThat(issued.getDocumentNumber()).isEqualTo("RC-" + card.getId());
		assertThat(issued.getPrintCount()).isEqualTo(1);
	}

	@Test
	void reprintKeepsFrozenMarksAfterACorrectionAndIsMarkedProvisionalThenSuperseded() throws Exception {
		Fixture fixture = setUpFixture("rc-pdf-correction");
		StudentEnrollment meera = enrol(fixture, "Meera", "ADM-611", 1);
		MarksEntry maths = examinationService.enterMarks(fixture.maths(), List.of(new MarkInput(meera, 90.0, false, null))).get(0);
		examinationService.enterMarks(fixture.science(), List.of(new MarkInput(meera, 85.0, false, null)));
		approveAll(fixture);
		ReportCard original = examinationService.publishReportCard(fixture.exam(), meera, fixture.scale());
		prepare(original.getId(), true);

		examinationService.correctMarksAfterPublish(maths, 60.0, false, "Re-checked");
		String provisional = pdfText(pdfService.renderPdf(prepare(original.getId(), true)));
		assertThat(provisional).contains("PROVISIONAL", "90.0").doesNotContain("60.0");

		ReportCard corrected = examinationService.publishReportCard(fixture.exam(), meera, fixture.scale());
		String superseded = pdfText(pdfService.renderPdf(prepare(original.getId(), true)));
		assertThat(superseded).contains("SUPERSEDED");
		assertThat(issuedDocumentRepository
				.findFirstByDocumentKindAndSubjectTypeAndSubjectIdOrderByIdDesc(IssuedDocumentKind.REPORT_CARD, "REPORT_CARD", original.getId())
				.orElseThrow().getStatus()).isEqualTo(IssuedDocumentStatus.CANCELLED);

		String fresh = pdfText(pdfService.renderPdf(prepare(corrected.getId(), true)));
		assertThat(fresh).contains("60.0", "145.0").doesNotContain("SUPERSEDED", "PROVISIONAL");
	}

	@Test
	void guardianDownloadsAreNotCountedAsOfficePrints() {
		Fixture fixture = setUpFixture("rc-pdf-guardian");
		StudentEnrollment meera = enrol(fixture, "Meera", "ADM-621", 1);
		examinationService.enterMarks(fixture.maths(), List.of(new MarkInput(meera, 70.0, false, null)));
		examinationService.enterMarks(fixture.science(), List.of(new MarkInput(meera, 70.0, false, null)));
		approveAll(fixture);
		ReportCard card = examinationService.publishReportCard(fixture.exam(), meera, fixture.scale());

		prepare(card.getId(), false);
		prepare(card.getId(), false);

		assertThat(issuedDocumentRepository
				.findFirstByDocumentKindAndSubjectTypeAndSubjectIdOrderByIdDesc(IssuedDocumentKind.REPORT_CARD, "REPORT_CARD", card.getId())
				.orElseThrow().getPrintCount()).isZero();
	}

	@Test
	void sectionPrintHasOnePagePerPublishedCardInNameOrder() throws Exception {
		Fixture fixture = setUpFixture("rc-pdf-section");
		StudentEnrollment zara = enrol(fixture, "Zara", "ADM-631", 1);
		StudentEnrollment aarav = enrol(fixture, "Aarav", "ADM-632", 2);
		examinationService.enterMarks(fixture.maths(), List.of(new MarkInput(zara, 50.0, false, null), new MarkInput(aarav, 95.0, false, null)));
		examinationService.enterMarks(fixture.science(), List.of(new MarkInput(zara, 40.0, false, null), new MarkInput(aarav, 92.0, false, null)));
		approveAll(fixture);
		examinationService.publishReportCard(fixture.exam(), zara, fixture.scale());
		examinationService.publishReportCard(fixture.exam(), aarav, fixture.scale());

		var prepared = prepareSection(fixture);
		byte[] pdf = pdfService.renderPdf(prepared);

		assertThat(prepared.cards()).extracting(c -> c.snapshot().studentName()).containsExactly("Aarav Nair", "Zara Nair");
		assertThat(pageCount(pdf)).isEqualTo(2);
		String text = pdfText(pdf);
		assertThat(text.indexOf("Aarav Nair")).isLessThan(text.indexOf("Zara Nair"));
	}

	@Test
	void sectionPrintWithNoPublishedCardsIsRejected() {
		Fixture fixture = setUpFixture("rc-pdf-empty");
		assertThatThrownBy(() -> prepareSection(fixture)).isInstanceOf(InvalidRequestException.class);
	}
}
