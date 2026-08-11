package com.operion.examination.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.examination.ExamSchedule;
import com.operion.examination.ExamScheduleRepository;
import com.operion.examination.ExaminationService;
import com.operion.examination.ExaminationService.MarkInput;
import com.operion.examination.MarksEntry;
import com.operion.examination.MarksEntryRepository;
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

	public MarksEntryController(ExaminationService examinationService, ExamScheduleRepository examScheduleRepository,
			StudentEnrollmentRepository studentEnrollmentRepository, MarksEntryRepository marksEntryRepository) {
		this.examinationService = examinationService;
		this.examScheduleRepository = examScheduleRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.marksEntryRepository = marksEntryRepository;
	}

	@PostMapping("/schedules/{scheduleId}/marks")
	@RequirePermission("MARKS_ENTER")
	public List<MarksEntryResponse> enter(@PathVariable Long scheduleId, @RequestBody EnterMarksRequest request) {
		ExamSchedule schedule = examScheduleRepository.findById(scheduleId)
				.orElseThrow(() -> new IllegalArgumentException("No exam schedule with id " + scheduleId));

		List<MarkInput> marks = request.marks().stream()
				.map(entry -> new MarkInput(findEnrollment(entry.studentEnrollmentId()), entry.marksObtained(), entry.absent(), entry.remarks()))
				.toList();

		return examinationService.enterMarks(schedule, marks).stream().map(MarksEntryResponse::from).toList();
	}

	@GetMapping("/schedules/{scheduleId}/marks")
	public List<MarksEntryResponse> list(@PathVariable Long scheduleId) {
		return marksEntryRepository.findByExamScheduleId(scheduleId).stream().map(MarksEntryResponse::from).toList();
	}

	@PatchMapping("/marks/{marksEntryId}")
	@RequirePermission("MARKS_CORRECT")
	public MarksEntryResponse correct(@PathVariable Long marksEntryId, @RequestBody CorrectMarksRequest request) {
		MarksEntry entry = marksEntryRepository.findById(marksEntryId)
				.orElseThrow(() -> new IllegalArgumentException("No marks entry with id " + marksEntryId));

		MarksEntry corrected = examinationService.correctMarks(entry, request.marksObtained(), request.absent(), request.remarks());
		return MarksEntryResponse.from(corrected);
	}

	private StudentEnrollment findEnrollment(Long enrollmentId) {
		return studentEnrollmentRepository.findById(enrollmentId)
				.orElseThrow(() -> new IllegalArgumentException("No student enrollment with id " + enrollmentId));
	}
}
