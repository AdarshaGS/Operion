package com.operion.examination.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.examination.ExamSchedule;
import com.operion.examination.ExamScheduleRepository;
import com.operion.examination.ExaminationService;
import com.operion.examination.ExaminationService.MarkInput;
import com.operion.examination.MarksEntry;
import com.operion.examination.MarksEntryRegisterRepository;
import com.operion.examination.MarksEntryRepository;
import com.operion.examination.ReportCardRepository;
import com.operion.examination.ReportCardStatus;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/examinations")
@RequirePermission("EXAM_VIEW")
public class MarksEntryController {

	private final ExaminationService examinationService;
	private final ExamScheduleRepository examScheduleRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final MarksEntryRepository marksEntryRepository;
	private final MarksEntryRegisterRepository marksEntryRegisterRepository;
	private final ReportCardRepository reportCardRepository;

	public MarksEntryController(ExaminationService examinationService, ExamScheduleRepository examScheduleRepository,
			StudentEnrollmentRepository studentEnrollmentRepository, MarksEntryRepository marksEntryRepository,
			MarksEntryRegisterRepository marksEntryRegisterRepository, ReportCardRepository reportCardRepository) {
		this.examinationService = examinationService;
		this.examScheduleRepository = examScheduleRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.marksEntryRepository = marksEntryRepository;
		this.marksEntryRegisterRepository = marksEntryRegisterRepository;
		this.reportCardRepository = reportCardRepository;
	}

	@PostMapping("/schedules/{scheduleId}/marks")
	@RequirePermission("MARKS_ENTER")
	public List<MarksEntryResponse> enter(@PathVariable Long scheduleId, @RequestBody EnterMarksRequest request) {
		ExamSchedule schedule = findSchedule(scheduleId);

		List<MarkInput> marks = request.marks().stream()
				.map(entry -> new MarkInput(findEnrollment(entry.studentEnrollmentId()), entry.marksObtained(), entry.absent(), entry.remarks()))
				.toList();

		return toResponses(examinationService.enterMarks(schedule, marks));
	}

	@GetMapping("/schedules/{scheduleId}/marks")
	public List<MarksEntryResponse> list(@PathVariable Long scheduleId) {
		return toResponses(marksEntryRepository.findByExamScheduleId(scheduleId));
	}

	/** Pre-publish correction only - rejected once a report card is published, see correctAfterPublish(). */
	@PatchMapping("/marks/{marksEntryId}")
	@RequirePermission("MARKS_CORRECT")
	public MarksEntryResponse correct(@PathVariable Long marksEntryId, @RequestBody CorrectMarksRequest request) {
		MarksEntry corrected =
				examinationService.correctMarks(findMarksEntry(marksEntryId), request.marksObtained(), request.absent(), request.remarks());
		return MarksEntryResponse.from(corrected, isPublished(corrected));
	}

	/** Post-publish correction (#138) - flags the published report card stale instead of silently desyncing it. */
	@PatchMapping("/marks/{marksEntryId}/correct-after-publish")
	@RequirePermission("MARKS_CORRECT_AFTER_PUBLISH")
	public MarksEntryResponse correctAfterPublish(@PathVariable Long marksEntryId, @RequestBody CorrectMarksRequest request) {
		MarksEntry corrected = examinationService.correctMarksAfterPublish(
				findMarksEntry(marksEntryId), request.marksObtained(), request.absent(), request.remarks());
		return MarksEntryResponse.from(corrected, isPublished(corrected));
	}

	@PostMapping("/schedules/{scheduleId}/submit")
	@RequirePermission("MARKS_SUBMIT")
	public MarksEntryRegisterResponse submit(@PathVariable Long scheduleId) {
		return MarksEntryRegisterResponse.from(examinationService.submitMarksRegister(findSchedule(scheduleId)));
	}

	@PostMapping("/schedules/{scheduleId}/approve")
	@RequirePermission("MARKS_APPROVE")
	public MarksEntryRegisterResponse approve(@PathVariable Long scheduleId) {
		return MarksEntryRegisterResponse.from(examinationService.approveMarksRegister(findSchedule(scheduleId)));
	}

	@GetMapping("/schedules/{scheduleId}/register")
	public MarksEntryRegisterResponse register(@PathVariable Long scheduleId) {
		return marksEntryRegisterRepository.findByExamScheduleId(scheduleId)
				.map(MarksEntryRegisterResponse::from)
				.orElseGet(() -> MarksEntryRegisterResponse.notStarted(scheduleId));
	}

	private List<MarksEntryResponse> toResponses(List<MarksEntry> entries) {
		return entries.stream().map(entry -> MarksEntryResponse.from(entry, isPublished(entry))).toList();
	}

	private boolean isPublished(MarksEntry entry) {
		return reportCardRepository.findByExamIdAndStudentEnrollmentIdAndStatus(
				entry.getExamSchedule().getExam().getId(), entry.getStudentEnrollment().getId(), ReportCardStatus.PUBLISHED).isPresent();
	}

	private ExamSchedule findSchedule(Long scheduleId) {
		return examScheduleRepository.findById(scheduleId)
				.orElseThrow(() -> new IllegalArgumentException("No exam schedule with id " + scheduleId));
	}

	private MarksEntry findMarksEntry(Long marksEntryId) {
		return marksEntryRepository.findById(marksEntryId)
				.orElseThrow(() -> new IllegalArgumentException("No marks entry with id " + marksEntryId));
	}

	private StudentEnrollment findEnrollment(Long enrollmentId) {
		return studentEnrollmentRepository.findById(enrollmentId)
				.orElseThrow(() -> new IllegalArgumentException("No student enrollment with id " + enrollmentId));
	}
}
