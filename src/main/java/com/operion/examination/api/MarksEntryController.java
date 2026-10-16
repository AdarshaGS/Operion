package com.operion.examination.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.common.InvalidRequestException;
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
import jakarta.validation.Valid;
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
	public List<MarksEntryResponse> enter(@PathVariable Long scheduleId, @Valid @RequestBody EnterMarksRequest request) {
		ExamSchedule schedule = findSchedule(scheduleId);

		List<MarkInput> marks = request.marks().stream()
				.map(entry -> {
					requireMarksInRange(schedule, entry.marksObtained(), entry.absent());
					return new MarkInput(findEnrollmentFor(schedule, entry.studentEnrollmentId()), entry.marksObtained(), entry.absent(), entry.remarks());
				})
				.toList();

		return toResponses(examinationService.enterMarks(schedule, marks));
	}

	@GetMapping("/schedules/{scheduleId}/marks")
	public List<MarksEntryResponse> list(@PathVariable Long scheduleId) {
		findSchedule(scheduleId);
		return toResponses(marksEntryRepository.findByExamScheduleId(scheduleId));
	}

	/** Pre-publish correction only - rejected once a report card is published, see correctAfterPublish(). */
	@PatchMapping("/marks/{marksEntryId}")
	@RequirePermission("MARKS_CORRECT")
	public MarksEntryResponse correct(@PathVariable Long marksEntryId, @RequestBody CorrectMarksRequest request) {
		MarksEntry entry = findMarksEntry(marksEntryId);
		requireMarksInRange(entry.getExamSchedule(), request.marksObtained(), request.absent());
		MarksEntry corrected = examinationService.correctMarks(entry, request.marksObtained(), request.absent(), request.remarks());
		return MarksEntryResponse.from(corrected, isPublished(corrected));
	}

	/** Post-publish correction (#138) - flags the published report card stale instead of silently desyncing it. */
	@PatchMapping("/marks/{marksEntryId}/correct-after-publish")
	@RequirePermission("MARKS_CORRECT_AFTER_PUBLISH")
	public MarksEntryResponse correctAfterPublish(@PathVariable Long marksEntryId, @RequestBody CorrectMarksRequest request) {
		MarksEntry entry = findMarksEntry(marksEntryId);
		requireMarksInRange(entry.getExamSchedule(), request.marksObtained(), request.absent());
		MarksEntry corrected = examinationService.correctMarksAfterPublish(entry, request.marksObtained(), request.absent(), request.remarks());
		return MarksEntryResponse.from(corrected, isPublished(corrected));
	}

	@PostMapping("/schedules/{scheduleId}/submit")
	@RequirePermission("MARKS_SUBMIT")
	public MarksEntryRegisterResponse submit(@PathVariable Long scheduleId) {
		ExamSchedule schedule = findSchedule(scheduleId);
		if (marksEntryRepository.findByExamScheduleId(scheduleId).isEmpty()) {
			throw new IllegalStateException("No marks have been entered for this schedule yet - enter marks before submitting");
		}
		return MarksEntryRegisterResponse.from(examinationService.submitMarksRegister(schedule));
	}

	@PostMapping("/schedules/{scheduleId}/approve")
	@RequirePermission("MARKS_APPROVE")
	public MarksEntryRegisterResponse approve(@PathVariable Long scheduleId) {
		return MarksEntryRegisterResponse.from(examinationService.approveMarksRegister(findSchedule(scheduleId)));
	}

	@GetMapping("/schedules/{scheduleId}/register")
	public MarksEntryRegisterResponse register(@PathVariable Long scheduleId) {
		findSchedule(scheduleId);
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

	/** A present student needs a mark within 0 and the schedule's maximum; an absent student's mark is ignored (stored as 0). */
	private void requireMarksInRange(ExamSchedule schedule, Double marksObtained, boolean absent) {
		if (absent) {
			return;
		}
		if (marksObtained == null) {
			throw new InvalidRequestException("A mark is required for a student who was present");
		}
		if (marksObtained < 0 || marksObtained > schedule.getMaxMarks()) {
			throw new InvalidRequestException("Marks must be between 0 and the maximum of " + schedule.getMaxMarks());
		}
	}

	/** The enrolment must be in the schedule's class and, for a section-only schedule, in that section. */
	private StudentEnrollment findEnrollmentFor(ExamSchedule schedule, Long enrollmentId) {
		StudentEnrollment enrollment = studentEnrollmentRepository.findById(enrollmentId)
				.orElseThrow(() -> new IllegalArgumentException("No student enrollment with id " + enrollmentId));
		boolean inClass = enrollment.getSection().getSchoolClass().getId().equals(schedule.getSchoolClass().getId());
		boolean inSection = schedule.getSection() == null || schedule.getSection().getId().equals(enrollment.getSection().getId());
		if (!inClass || !inSection) {
			throw new InvalidRequestException("Student enrollment " + enrollmentId + " is not in the class"
					+ (schedule.getSection() == null ? "" : " section") + " this exam schedule is for");
		}
		return enrollment;
	}
}
