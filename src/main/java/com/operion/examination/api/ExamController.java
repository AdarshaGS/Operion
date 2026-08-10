package com.operion.examination.api;

import java.util.List;

import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.examination.Exam;
import com.operion.examination.ExamRepository;
import com.operion.examination.ExamSchedule;
import com.operion.examination.ExamScheduleRepository;
import com.operion.examination.ExamType;
import com.operion.examination.ExaminationService;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/examinations/exams")
public class ExamController {

	private final ExaminationService examinationService;
	private final ExamRepository examRepository;
	private final ExamScheduleRepository examScheduleRepository;
	private final AcademicYearRepository academicYearRepository;
	private final SchoolClassRepository schoolClassRepository;
	private final SubjectRepository subjectRepository;

	public ExamController(ExaminationService examinationService, ExamRepository examRepository,
			ExamScheduleRepository examScheduleRepository, AcademicYearRepository academicYearRepository,
			SchoolClassRepository schoolClassRepository, SubjectRepository subjectRepository) {
		this.examinationService = examinationService;
		this.examRepository = examRepository;
		this.examScheduleRepository = examScheduleRepository;
		this.academicYearRepository = academicYearRepository;
		this.schoolClassRepository = schoolClassRepository;
		this.subjectRepository = subjectRepository;
	}

	@PostMapping
	public ExamResponse create(@RequestBody CreateExamRequest request) {
		AcademicYear academicYear = academicYearRepository.findById(request.academicYearId())
				.orElseThrow(() -> new IllegalArgumentException("No academic year with id " + request.academicYearId()));

		Exam exam = examinationService.createExam(academicYear, request.name(), ExamType.valueOf(request.examType()));
		return ExamResponse.from(exam);
	}

	@GetMapping
	public List<ExamResponse> list(@RequestParam Long academicYearId) {
		return examRepository.findByAcademicYearId(academicYearId).stream().map(ExamResponse::from).toList();
	}

	@PostMapping("/{examId}/schedules")
	public ExamScheduleResponse addSchedule(@PathVariable Long examId, @RequestBody CreateExamScheduleRequest request) {
		Exam exam = findExam(examId);
		SchoolClass schoolClass = schoolClassRepository.findById(request.schoolClassId())
				.orElseThrow(() -> new IllegalArgumentException("No school class with id " + request.schoolClassId()));
		Subject subject = subjectRepository.findById(request.subjectId())
				.orElseThrow(() -> new IllegalArgumentException("No subject with id " + request.subjectId()));

		ExamSchedule schedule = examinationService.addSchedule(
				exam, schoolClass, subject, request.examDate(), request.maxMarks(), request.passMarks());
		return ExamScheduleResponse.from(schedule);
	}

	@GetMapping("/{examId}/schedules")
	public List<ExamScheduleResponse> listSchedules(@PathVariable Long examId) {
		return examScheduleRepository.findByExamId(examId).stream().map(ExamScheduleResponse::from).toList();
	}

	private Exam findExam(Long examId) {
		return examRepository.findById(examId).orElseThrow(() -> new IllegalArgumentException("No exam with id " + examId));
	}
}
