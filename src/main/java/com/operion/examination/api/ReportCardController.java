package com.operion.examination.api;

import java.util.List;

import com.operion.academic.SchoolClass;
import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.document.DocumentTemplateRepository;
import com.operion.document.pdf.PdfResponses;
import com.operion.document.DocumentType;
import com.operion.document.api.DocumentTemplateResponse;
import com.operion.examination.Exam;
import com.operion.examination.ExamRepository;
import com.operion.examination.ExamSchedule;
import com.operion.examination.ExamScheduleRepository;
import com.operion.examination.ExaminationNotificationService;
import com.operion.examination.ExaminationService;
import com.operion.examination.GradingScale;
import com.operion.examination.GradingScaleRepository;
import com.operion.examination.MarksEntry;
import com.operion.examination.MarksEntryRepository;
import com.operion.examination.ReportCard;
import com.operion.examination.ReportCardPdfService;
import com.operion.examination.ReportCardRepository;
import com.operion.identity.Person;
import com.operion.organisation.OrganisationBranding;
import com.operion.organisation.OrganisationBrandingRepository;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/examinations")
@RequirePermission("EXAM_VIEW")
public class ReportCardController {

	private final ExaminationService examinationService;
	private final ExaminationNotificationService examinationNotificationService;
	private final ExamRepository examRepository;
	private final ExamScheduleRepository examScheduleRepository;
	private final GradingScaleRepository gradingScaleRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final MarksEntryRepository marksEntryRepository;
	private final ReportCardRepository reportCardRepository;
	private final OrganisationBrandingRepository organisationBrandingRepository;
	private final DocumentTemplateRepository documentTemplateRepository;
	private final ReportCardPdfService reportCardPdfService;

	public ReportCardController(ExaminationService examinationService, ExaminationNotificationService examinationNotificationService,
			ExamRepository examRepository,
			ExamScheduleRepository examScheduleRepository, GradingScaleRepository gradingScaleRepository,
			StudentEnrollmentRepository studentEnrollmentRepository, MarksEntryRepository marksEntryRepository,
			ReportCardRepository reportCardRepository, OrganisationBrandingRepository organisationBrandingRepository,
			DocumentTemplateRepository documentTemplateRepository, ReportCardPdfService reportCardPdfService) {
		this.examinationService = examinationService;
		this.examinationNotificationService = examinationNotificationService;
		this.examRepository = examRepository;
		this.examScheduleRepository = examScheduleRepository;
		this.gradingScaleRepository = gradingScaleRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.marksEntryRepository = marksEntryRepository;
		this.reportCardRepository = reportCardRepository;
		this.organisationBrandingRepository = organisationBrandingRepository;
		this.documentTemplateRepository = documentTemplateRepository;
		this.reportCardPdfService = reportCardPdfService;
	}

	/** One student's report card as a PDF. The first print freezes it in the issued-document register. */
	@GetMapping("/report-cards/{reportCardId}/report-card.pdf")
	public ResponseEntity<byte[]> pdf(@PathVariable Long reportCardId) {
		ReportCardPdfService.PreparedReportCards prepared = reportCardPdfService.prepare(reportCardId, true);
		return PdfResponses.attachment(reportCardPdfService.renderPdf(prepared), prepared.fileName());
	}

	/** Every published report card of one section for an exam, one page per student - for printing a whole class. */
	@GetMapping("/exams/{examId}/report-cards.pdf")
	public ResponseEntity<byte[]> sectionPdf(@PathVariable Long examId, @RequestParam Long sectionId) {
		ReportCardPdfService.PreparedReportCards prepared = reportCardPdfService.prepareSection(examId, sectionId);
		return PdfResponses.attachment(reportCardPdfService.renderPdf(prepared), prepared.fileName());
	}

	/** Publishes a fresh report card, or - if the current one is stale (#138) - supersedes it and republishes. */
	@PostMapping("/exams/{examId}/report-cards")
	@RequirePermission("REPORT_CARD_PUBLISH")
	public ReportCardResponse publish(@PathVariable Long examId, @Valid @RequestBody PublishReportCardRequest request) {
		Exam exam = examRepository.findById(examId).orElseThrow(() -> new IllegalArgumentException("No exam with id " + examId));
		StudentEnrollment enrollment = studentEnrollmentRepository.findById(request.studentEnrollmentId())
				.orElseThrow(() -> new IllegalArgumentException("No student enrollment with id " + request.studentEnrollmentId()));
		GradingScale gradingScale = gradingScaleRepository.findById(request.gradingScaleId())
				.orElseThrow(() -> new IllegalArgumentException("No grading scale with id " + request.gradingScaleId()));

		ReportCard reportCard = examinationService.publishReportCard(exam, enrollment, gradingScale);
		examinationNotificationService.notifyResultPublished(reportCard);
		return ReportCardResponse.from(reportCard);
	}

	@GetMapping("/report-cards/{reportCardId}")
	public ReportCardResponse get(@PathVariable Long reportCardId) {
		return ReportCardResponse.from(findReportCard(reportCardId));
	}

	@GetMapping("/report-cards")
	public List<ReportCardResponse> list(@RequestParam Long studentEnrollmentId) {
		return reportCardRepository.findByStudentEnrollmentId(studentEnrollmentId).stream().map(ReportCardResponse::from).toList();
	}

	/** Resolved render data for a printable/branded report card document. Per #243. */
	@GetMapping("/report-cards/{reportCardId}/render")
	public ReportCardRenderResponse render(@PathVariable Long reportCardId) {
		ReportCard reportCard = findReportCard(reportCardId);
		StudentEnrollment enrollment = reportCard.getStudentEnrollment();
		Student student = enrollment.getStudent();
		Person person = student.getPerson();
		SchoolClass schoolClass = enrollment.getSection().getSchoolClass();

		List<ReportCardRenderResponse.SubjectMark> subjects =
				examScheduleRepository.findApplicableToSection(reportCard.getExam().getId(), schoolClass.getId(), enrollment.getSection().getId())
						.stream()
						.map(schedule -> toSubjectMark(schedule, enrollment))
						.toList();

		OrganisationBranding branding = organisationBrandingRepository.findById(TenantContext.getOrganisationId()).orElse(null);
		DocumentTemplateResponse template = documentTemplateRepository.findByDocumentType(DocumentType.REPORT_CARD)
				.map(DocumentTemplateResponse::from)
				.orElseGet(() -> DocumentTemplateResponse.defaults(DocumentType.REPORT_CARD));

		return new ReportCardRenderResponse(
				branding == null ? null : branding.getLogoRef(), branding == null ? null : branding.getStampRef(),
				branding == null ? null : branding.getSignatureRef(), branding == null ? null : branding.getSchoolNameOverride(),
				branding == null ? null : branding.getAddressLine(), branding == null ? null : branding.getAffiliationText(),
				branding == null ? null : branding.getFooterText(),
				template.templateStyle().name(), template.pageSize(), template.fontStyle(), template.fontSize(), template.headerSubtext(),
				fullName(person), student.getAdmissionNumber(), className(schoolClass), enrollment.getSection().getName(),
				reportCard.getExam().getName(), reportCard.getExam().getExamType().name(), reportCard.getExam().getAcademicYear().getName(),
				subjects,
				reportCard.getTotalMarksObtained(), reportCard.getTotalMaxMarks(), reportCard.getPercentage(), reportCard.getOverallGrade(),
				reportCard.isPassed(), reportCard.getClassRank(), reportCard.getStatus().name(), reportCard.isStale(),
				reportCard.getCreatedBy(), reportCard.getCreatedAt());
	}

	private ReportCardRenderResponse.SubjectMark toSubjectMark(ExamSchedule schedule, StudentEnrollment enrollment) {
		MarksEntry entry = marksEntryRepository.findByExamScheduleIdAndStudentEnrollmentId(schedule.getId(), enrollment.getId()).orElse(null);
		boolean subjectPassed = entry != null && !entry.isAbsent() && entry.getMarksObtained() >= schedule.getPassMarks();
		return new ReportCardRenderResponse.SubjectMark(schedule.getSubject().getName(), schedule.getMaxMarks(), schedule.getPassMarks(),
				entry == null ? null : entry.getMarksObtained(), entry != null && entry.isAbsent(), subjectPassed, entry == null ? null : entry.getRank());
	}

	private String fullName(Person person) {
		return person.getLastName() == null ? person.getFirstName() : person.getFirstName() + " " + person.getLastName();
	}

	private String className(SchoolClass schoolClass) {
		return schoolClass.getDisplayName() != null ? schoolClass.getDisplayName() : schoolClass.getGradeLevel().getName();
	}

	private ReportCard findReportCard(Long reportCardId) {
		return reportCardRepository.findById(reportCardId)
				.orElseThrow(() -> new IllegalArgumentException("No report card with id " + reportCardId));
	}
}
