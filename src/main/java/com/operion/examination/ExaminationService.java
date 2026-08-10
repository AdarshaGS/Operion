package com.operion.examination;

import java.time.LocalDate;
import java.util.List;

import com.operion.academic.SchoolClass;
import com.operion.academic.Subject;
import com.operion.audit.AuditLogService;
import com.operion.organisation.AcademicYear;
import com.operion.student.StudentEnrollment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns exam/schedule setup, grading scale bands, marks entry + correction, and report
 * card publishing. Marks corrections mutate MarksEntry in place and mirror to the shared
 * AuditLog rather than a dedicated correction table - see MarksEntry's class doc for why
 * this diverges from Attendance's typed-correction-table pattern.
 */
@Service
public class ExaminationService {

	private final ExamRepository examRepository;
	private final ExamScheduleRepository examScheduleRepository;
	private final GradingScaleRepository gradingScaleRepository;
	private final GradingScaleBandRepository gradingScaleBandRepository;
	private final MarksEntryRepository marksEntryRepository;
	private final ReportCardRepository reportCardRepository;
	private final AuditLogService auditLogService;

	public ExaminationService(ExamRepository examRepository, ExamScheduleRepository examScheduleRepository,
			GradingScaleRepository gradingScaleRepository, GradingScaleBandRepository gradingScaleBandRepository,
			MarksEntryRepository marksEntryRepository, ReportCardRepository reportCardRepository, AuditLogService auditLogService) {
		this.examRepository = examRepository;
		this.examScheduleRepository = examScheduleRepository;
		this.gradingScaleRepository = gradingScaleRepository;
		this.gradingScaleBandRepository = gradingScaleBandRepository;
		this.marksEntryRepository = marksEntryRepository;
		this.reportCardRepository = reportCardRepository;
		this.auditLogService = auditLogService;
	}

	public Exam createExam(AcademicYear academicYear, String name, ExamType examType) {
		return examRepository.save(new Exam(academicYear, name, examType));
	}

	public ExamSchedule addSchedule(
			Exam exam, SchoolClass schoolClass, Subject subject, LocalDate examDate, Double maxMarks, Double passMarks) {
		return examScheduleRepository.save(new ExamSchedule(exam, schoolClass, subject, examDate, maxMarks, passMarks));
	}

	@Transactional
	public GradingScale createGradingScale(String name, boolean defaultScale, List<BandInput> bands) {
		GradingScale scale = gradingScaleRepository.save(new GradingScale(name, defaultScale));
		for (BandInput band : bands) {
			gradingScaleBandRepository.save(new GradingScaleBand(scale, band.grade(), band.minPercentage(), band.remark()));
		}
		return scale;
	}

	/** Rejects a second entry for the same (schedule, enrollment) - use correctMarks() to change an already-entered row. */
	@Transactional
	public List<MarksEntry> enterMarks(ExamSchedule examSchedule, List<MarkInput> marks) {
		return marks.stream().map(mark -> {
			marksEntryRepository.findByExamScheduleIdAndStudentEnrollmentId(examSchedule.getId(), mark.enrollment().getId())
					.ifPresent(existing -> {
						throw new IllegalStateException("Student enrollment " + mark.enrollment().getId()
								+ " already has marks entered for exam schedule " + examSchedule.getId());
					});
			return marksEntryRepository.save(
					new MarksEntry(examSchedule, mark.enrollment(), mark.marksObtained(), mark.absent(), mark.remarks()));
		}).toList();
	}

	@Transactional
	public MarksEntry correctMarks(MarksEntry marksEntry, Double marksObtained, boolean absent, String remarks) {
		Double previousMarks = marksEntry.getMarksObtained();
		marksEntry.correctMarks(marksObtained, absent, remarks);
		MarksEntry saved = marksEntryRepository.save(marksEntry);
		auditLogService.record("MarksEntry", marksEntry.getId(), "CORRECTED", previousMarks, saved.getMarksObtained());
		return saved;
	}

	/**
	 * Requires every ExamSchedule for the student's class to already have a MarksEntry -
	 * refuses to publish with a silently-missing subject. Rejects a duplicate publish.
	 */
	@Transactional
	public ReportCard publishReportCard(Exam exam, StudentEnrollment studentEnrollment, GradingScale gradingScale) {
		reportCardRepository.findByExamIdAndStudentEnrollmentId(exam.getId(), studentEnrollment.getId()).ifPresent(existing -> {
			throw new IllegalStateException("Report card already published for exam " + exam.getId() + " and enrollment " + studentEnrollment.getId());
		});

		Long schoolClassId = studentEnrollment.getSection().getSchoolClass().getId();
		List<ExamSchedule> schedules = examScheduleRepository.findByExamIdAndSchoolClassId(exam.getId(), schoolClassId);
		if (schedules.isEmpty()) {
			throw new IllegalStateException("No exam schedules found for exam " + exam.getId() + " and class " + schoolClassId);
		}

		double totalObtained = 0;
		double totalMax = 0;
		for (ExamSchedule schedule : schedules) {
			MarksEntry entry = marksEntryRepository.findByExamScheduleIdAndStudentEnrollmentId(schedule.getId(), studentEnrollment.getId())
					.orElseThrow(() -> new IllegalStateException(
							"Marks missing for subject " + schedule.getSubject().getId() + " - cannot publish report card"));
			totalObtained += entry.getMarksObtained();
			totalMax += schedule.getMaxMarks();
		}

		double percentage = totalMax == 0 ? 0 : (totalObtained / totalMax) * 100;
		String grade = resolveGrade(gradingScale, percentage);

		return reportCardRepository.save(new ReportCard(exam, studentEnrollment, totalObtained, totalMax, percentage, grade));
	}

	private String resolveGrade(GradingScale gradingScale, double percentage) {
		return gradingScaleBandRepository.findByGradingScaleIdOrderByMinPercentageDesc(gradingScale.getId()).stream()
				.filter(band -> percentage >= band.getMinPercentage())
				.findFirst()
				.map(GradingScaleBand::getGrade)
				.orElseThrow(() -> new IllegalStateException("No grading band covers percentage " + percentage));
	}

	public record BandInput(String grade, Double minPercentage, String remark) {
	}

	public record MarkInput(StudentEnrollment enrollment, Double marksObtained, boolean absent, String remarks) {
	}
}
