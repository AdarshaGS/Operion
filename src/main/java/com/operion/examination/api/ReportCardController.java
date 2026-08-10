package com.operion.examination.api;

import java.util.List;

import com.operion.examination.Exam;
import com.operion.examination.ExamRepository;
import com.operion.examination.ExaminationService;
import com.operion.examination.GradingScale;
import com.operion.examination.GradingScaleRepository;
import com.operion.examination.ReportCard;
import com.operion.examination.ReportCardRepository;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/examinations")
public class ReportCardController {

	private final ExaminationService examinationService;
	private final ExamRepository examRepository;
	private final GradingScaleRepository gradingScaleRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final ReportCardRepository reportCardRepository;

	public ReportCardController(ExaminationService examinationService, ExamRepository examRepository,
			GradingScaleRepository gradingScaleRepository, StudentEnrollmentRepository studentEnrollmentRepository,
			ReportCardRepository reportCardRepository) {
		this.examinationService = examinationService;
		this.examRepository = examRepository;
		this.gradingScaleRepository = gradingScaleRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.reportCardRepository = reportCardRepository;
	}

	@PostMapping("/exams/{examId}/report-cards")
	public ReportCardResponse publish(@PathVariable Long examId, @RequestBody PublishReportCardRequest request) {
		Exam exam = examRepository.findById(examId).orElseThrow(() -> new IllegalArgumentException("No exam with id " + examId));
		StudentEnrollment enrollment = studentEnrollmentRepository.findById(request.studentEnrollmentId())
				.orElseThrow(() -> new IllegalArgumentException("No student enrollment with id " + request.studentEnrollmentId()));
		GradingScale gradingScale = gradingScaleRepository.findById(request.gradingScaleId())
				.orElseThrow(() -> new IllegalArgumentException("No grading scale with id " + request.gradingScaleId()));

		ReportCard reportCard = examinationService.publishReportCard(exam, enrollment, gradingScale);
		return ReportCardResponse.from(reportCard);
	}

	@GetMapping("/report-cards/{reportCardId}")
	public ReportCardResponse get(@PathVariable Long reportCardId) {
		return ReportCardResponse.from(reportCardRepository.findById(reportCardId)
				.orElseThrow(() -> new IllegalArgumentException("No report card with id " + reportCardId)));
	}

	@GetMapping("/report-cards")
	public List<ReportCardResponse> list(@RequestParam Long studentEnrollmentId) {
		return reportCardRepository.findByStudentEnrollmentId(studentEnrollmentId).stream().map(ReportCardResponse::from).toList();
	}
}
