package com.operion.examination;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.operion.academic.SchoolClass;
import com.operion.common.InvalidRequestException;
import com.operion.common.TenantContext;
import com.operion.document.DocumentBranding;
import com.operion.document.DocumentBrandingService;
import com.operion.document.DocumentTemplate;
import com.operion.document.DocumentTemplateRepository;
import com.operion.document.DocumentType;
import com.operion.document.IssuedDocument;
import com.operion.document.IssuedDocumentKind;
import com.operion.document.IssuedDocumentRepository;
import com.operion.document.TemplateStyle;
import com.operion.document.pdf.DocumentHtmlRenderer;
import com.operion.document.pdf.PageFormat;
import com.operion.document.pdf.PdfRenderer;
import com.operion.identity.Person;
import com.operion.student.StudentEnrollment;

import tools.jackson.databind.ObjectMapper;

/**
 * Report cards as PDFs, on the same engine and issued-document register as fee receipts.
 * A ReportCard row is already a publish-time snapshot (a correction republishes a new row),
 * so the first print of a row freezes its printed values - including the subject marks it
 * joins from MarksEntry - into the register, and every later print re-renders that.
 * A SUPERSEDED card still prints, watermarked; a stale (under correction) card prints
 * watermarked PROVISIONAL. Reprints are not marked DUPLICATE - schools reprint report
 * cards routinely - but every office print is counted.
 *
 * {@link #prepare}/{@link #prepareSection} run in a transaction; {@link #renderPdf} does
 * the layout outside it, as ReceiptService does.
 */
@Service
public class ReportCardPdfService {

	public static final String SUBJECT_TYPE = "REPORT_CARD";

	public record SubjectLine(String subjectName, Double maxMarks, Double passMarks, Double marksObtained, boolean absent, boolean passed) {
	}

	/** Everything printed, frozen at first issue. */
	public record ReportCardSnapshot(String documentNumber, String studentName, String admissionNumber, String classSection,
			String examName, String examType, String academicYear, List<SubjectLine> subjects, Double totalMarksObtained,
			Double totalMaxMarks, Double percentage, String overallGrade, boolean passed, Integer classRank,
			DocumentBranding school, Instant issuedAt) {
	}

	/** One printable card: the frozen snapshot plus its state at print time. */
	public record PrintedCard(ReportCardSnapshot snapshot, boolean superseded, boolean provisional) {
	}

	public record PreparedReportCards(List<PrintedCard> cards, PageFormat pageFormat, TemplateStyle templateStyle, int fontSize,
			String headerSubtext, String fileName) {
	}

	private final ReportCardRepository reportCardRepository;
	private final ExamScheduleRepository examScheduleRepository;
	private final MarksEntryRepository marksEntryRepository;
	private final IssuedDocumentRepository issuedDocumentRepository;
	private final DocumentTemplateRepository documentTemplateRepository;
	private final DocumentBrandingService brandingService;
	private final DocumentHtmlRenderer htmlRenderer;
	private final PdfRenderer pdfRenderer;
	private final ObjectMapper objectMapper;

	public ReportCardPdfService(ReportCardRepository reportCardRepository, ExamScheduleRepository examScheduleRepository,
			MarksEntryRepository marksEntryRepository, IssuedDocumentRepository issuedDocumentRepository,
			DocumentTemplateRepository documentTemplateRepository, DocumentBrandingService brandingService,
			DocumentHtmlRenderer htmlRenderer, PdfRenderer pdfRenderer, ObjectMapper objectMapper) {
		this.reportCardRepository = reportCardRepository;
		this.examScheduleRepository = examScheduleRepository;
		this.marksEntryRepository = marksEntryRepository;
		this.issuedDocumentRepository = issuedDocumentRepository;
		this.documentTemplateRepository = documentTemplateRepository;
		this.brandingService = brandingService;
		this.htmlRenderer = htmlRenderer;
		this.pdfRenderer = pdfRenderer;
		this.objectMapper = objectMapper;
	}

	/** @param officePrint true for a staff print (counted in the register), false for a guardian's own download. */
	@Transactional
	public PreparedReportCards prepare(Long reportCardId, boolean officePrint) {
		ReportCard reportCard = reportCardRepository.findById(reportCardId)
				.orElseThrow(() -> new IllegalArgumentException("No report card with id " + reportCardId));
		PrintedCard card = printed(reportCard, officePrint);
		return withTemplate(List.of(card), "report-card-" + card.snapshot().admissionNumber() + "-" + slug(card.snapshot().examName()));
	}

	/** Every PUBLISHED report card of one section for an exam, one page each, ordered by student name. */
	@Transactional
	public PreparedReportCards prepareSection(Long examId, Long sectionId) {
		List<ReportCard> reportCards = reportCardRepository.findByExamIdAndStudentEnrollment_Section_IdAndStatus(
				examId, sectionId, ReportCardStatus.PUBLISHED);
		if (reportCards.isEmpty()) {
			throw new InvalidRequestException("No published report cards for this exam in section " + sectionId);
		}
		List<PrintedCard> cards = new ArrayList<>();
		for (ReportCard reportCard : reportCards) {
			cards.add(printed(reportCard, true));
		}
		cards.sort((a, b) -> a.snapshot().studentName().compareToIgnoreCase(b.snapshot().studentName()));
		ReportCardSnapshot first = cards.get(0).snapshot();
		return withTemplate(cards, "report-cards-" + slug(first.classSection()) + "-" + slug(first.examName()));
	}

	public byte[] renderPdf(PreparedReportCards prepared) {
		// Branding images are the school's current ones - snapshots hold text and references only.
		DocumentBranding school = prepared.cards().get(0).snapshot().school();
		Map<String, Object> variables = new LinkedHashMap<>();
		variables.put("r", prepared);
		variables.put("page", prepared.pageFormat());
		variables.put("logo", brandingService.imageDataUri(school.logoRef()).orElse(null));
		variables.put("stamp", brandingService.imageDataUri(school.stampRef()).orElse(null));
		variables.put("signature", brandingService.imageDataUri(school.signatureRef()).orElse(null));
		variables.put("styleClass", "style-" + prepared.templateStyle().name().toLowerCase());
		return pdfRenderer.render(htmlRenderer.render("report-card", variables));
	}

	private PrintedCard printed(ReportCard reportCard, boolean officePrint) {
		IssuedDocument document = issuedDocumentRepository
				.findFirstByDocumentKindAndSubjectTypeAndSubjectIdOrderByIdDesc(IssuedDocumentKind.REPORT_CARD, SUBJECT_TYPE, reportCard.getId())
				.orElseGet(() -> issue(reportCard));
		boolean superseded = reportCard.getStatus() == ReportCardStatus.SUPERSEDED;
		if (superseded) {
			document.cancel("Superseded by a corrected report card");
		}
		if (officePrint) {
			document.recordPrint(TenantContext.getActorId());
		}
		issuedDocumentRepository.save(document);
		ReportCardSnapshot snapshot = objectMapper.readValue(document.getDataSnapshot(), ReportCardSnapshot.class);
		return new PrintedCard(snapshot, superseded, !superseded && reportCard.isStale());
	}

	private PreparedReportCards withTemplate(List<PrintedCard> cards, String fileName) {
		DocumentTemplate template = documentTemplateRepository.findByDocumentType(DocumentType.REPORT_CARD)
				.orElseGet(() -> new DocumentTemplate(DocumentType.REPORT_CARD));
		PageFormat format = PageFormat.fromCode(template.getPageSize());
		// Thermal paper is for receipts only.
		if (format.narrow()) {
			format = PageFormat.A4;
		}
		return new PreparedReportCards(cards, format, template.getTemplateStyle(), template.getFontSize(), template.getHeaderSubtext(), fileName);
	}

	private IssuedDocument issue(ReportCard reportCard) {
		ReportCardSnapshot snapshot = buildSnapshot(reportCard);
		String json = objectMapper.writeValueAsString(snapshot);
		return issuedDocumentRepository.save(new IssuedDocument(IssuedDocumentKind.REPORT_CARD, snapshot.documentNumber(), SUBJECT_TYPE,
				reportCard.getId(), json, sha256(json)));
	}

	private ReportCardSnapshot buildSnapshot(ReportCard reportCard) {
		StudentEnrollment enrollment = reportCard.getStudentEnrollment();
		SchoolClass schoolClass = enrollment.getSection().getSchoolClass();
		Exam exam = reportCard.getExam();
		List<SubjectLine> subjects = examScheduleRepository
				.findApplicableToSection(exam.getId(), schoolClass.getId(), enrollment.getSection().getId()).stream()
				.map(schedule -> subjectLine(schedule, enrollment))
				.toList();
		return new ReportCardSnapshot("RC-" + reportCard.getId(), fullName(enrollment.getStudent().getPerson()),
				enrollment.getStudent().getAdmissionNumber(), schoolClass.label() + " - " + enrollment.getSection().getName(),
				exam.getName(), exam.getExamType().name().replace('_', ' '), exam.getAcademicYear().getName(), subjects,
				reportCard.getTotalMarksObtained(), reportCard.getTotalMaxMarks(), reportCard.getPercentage(), reportCard.getOverallGrade(),
				reportCard.isPassed(), reportCard.getClassRank(), brandingService.current(), Instant.now());
	}

	private SubjectLine subjectLine(ExamSchedule schedule, StudentEnrollment enrollment) {
		MarksEntry entry = marksEntryRepository.findByExamScheduleIdAndStudentEnrollmentId(schedule.getId(), enrollment.getId()).orElse(null);
		boolean absent = entry != null && entry.isAbsent();
		boolean passed = entry != null && !absent && entry.getMarksObtained() >= schedule.getPassMarks();
		return new SubjectLine(schedule.getSubject().getName(), schedule.getMaxMarks(), schedule.getPassMarks(),
				entry == null || absent ? null : entry.getMarksObtained(), absent, passed);
	}

	private static String fullName(Person person) {
		return person.getLastName() == null ? person.getFirstName() : person.getFirstName() + " " + person.getLastName();
	}

	private static String slug(String value) {
		return value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9]+", "-");
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
