package com.operion.examination;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

import com.operion.academic.SchoolClass;
import com.operion.academic.Section;
import com.operion.academic.Subject;
import com.operion.audit.AuditLogService;
import com.operion.common.TenantContext;
import com.operion.organisation.AcademicYear;
import com.operion.student.StudentEnrollment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns exam/schedule setup, grading scale bands, marks entry + the draft/submit/approve
 * review workflow (#134), correction (both pre- and post-publish, #138), report card
 * publishing/republishing, and ranking (#136). Marks corrections mutate MarksEntry in
 * place and mirror to the shared AuditLog rather than a dedicated correction table - see
 * MarksEntry's class doc for why this diverges from Attendance's typed-correction-table
 * pattern.
 */
@Service
public class ExaminationService {

	private final ExamRepository examRepository;
	private final ExamScheduleRepository examScheduleRepository;
	private final GradingScaleRepository gradingScaleRepository;
	private final GradingScaleBandRepository gradingScaleBandRepository;
	private final MarksEntryRepository marksEntryRepository;
	private final MarksEntryRegisterRepository marksEntryRegisterRepository;
	private final ReportCardRepository reportCardRepository;
	private final ExaminationSettingsRepository examinationSettingsRepository;
	private final AuditLogService auditLogService;

	public ExaminationService(ExamRepository examRepository, ExamScheduleRepository examScheduleRepository,
			GradingScaleRepository gradingScaleRepository, GradingScaleBandRepository gradingScaleBandRepository,
			MarksEntryRepository marksEntryRepository, MarksEntryRegisterRepository marksEntryRegisterRepository,
			ReportCardRepository reportCardRepository, ExaminationSettingsRepository examinationSettingsRepository,
			AuditLogService auditLogService) {
		this.examRepository = examRepository;
		this.examScheduleRepository = examScheduleRepository;
		this.gradingScaleRepository = gradingScaleRepository;
		this.gradingScaleBandRepository = gradingScaleBandRepository;
		this.marksEntryRepository = marksEntryRepository;
		this.marksEntryRegisterRepository = marksEntryRegisterRepository;
		this.reportCardRepository = reportCardRepository;
		this.examinationSettingsRepository = examinationSettingsRepository;
		this.auditLogService = auditLogService;
	}

	public Exam createExam(AcademicYear academicYear, String name, ExamType examType) {
		return examRepository.save(new Exam(academicYear, name, examType));
	}

	/** Whole-class schedule (applies to every section). */
	public ExamSchedule addSchedule(
			Exam exam, SchoolClass schoolClass, Subject subject, LocalDate examDate, Double maxMarks, Double passMarks) {
		return addSchedule(exam, schoolClass, null, subject, examDate, maxMarks, passMarks);
	}

	/**
	 * {@code section} null means the schedule applies to every section of the class.
	 * Rejects adding a whole-class schedule alongside an existing per-section one (or vice
	 * versa) for the same subject on the same exam - see ExamSchedule's class doc. Per #139.
	 */
	@Transactional
	public ExamSchedule addSchedule(Exam exam, SchoolClass schoolClass, Section section, Subject subject,
			LocalDate examDate, Double maxMarks, Double passMarks) {
		List<ExamSchedule> existing =
				examScheduleRepository.findByExamIdAndSchoolClassIdAndSubjectId(exam.getId(), schoolClass.getId(), subject.getId());
		boolean conflicts = section == null ? !existing.isEmpty()
				: existing.stream().anyMatch(s -> s.getSection() == null || s.getSection().getId().equals(section.getId()));
		if (conflicts) {
			throw new IllegalStateException("A schedule already covers subject " + subject.getId() + " for "
					+ (section == null ? "the whole class" : "this section") + " on exam " + exam.getId());
		}
		return examScheduleRepository.save(new ExamSchedule(exam, schoolClass, section, subject, examDate, maxMarks, passMarks));
	}

	@Transactional
	public GradingScale createGradingScale(String name, boolean defaultScale, List<BandInput> bands) {
		GradingScale scale = gradingScaleRepository.save(new GradingScale(name, defaultScale));
		for (BandInput band : bands) {
			gradingScaleBandRepository.save(new GradingScaleBand(scale, band.grade(), band.minPercentage(), band.remark()));
		}
		return scale;
	}

	/**
	 * Rejects a second entry for the same (schedule, enrollment) - use correctMarks() to
	 * change an already-entered row. Only allowed while the schedule's MarksEntryRegister is
	 * DRAFT (#134); the register is created lazily, DRAFT, on the first call for a schedule.
	 */
	@Transactional
	public List<MarksEntry> enterMarks(ExamSchedule examSchedule, List<MarkInput> marks) {
		MarksEntryRegister register = registerFor(examSchedule);
		if (register.getRegisterStatus() != MarksEntryRegisterStatus.DRAFT) {
			throw new IllegalStateException(
					"Marks entry register for schedule " + examSchedule.getId() + " is " + register.getRegisterStatus() + ", cannot enter marks");
		}
		List<MarksEntry> entered = marks.stream().map(mark -> {
			marksEntryRepository.findByExamScheduleIdAndStudentEnrollmentId(examSchedule.getId(), mark.enrollment().getId())
					.ifPresent(existing -> {
						throw new IllegalStateException("Student enrollment " + mark.enrollment().getId()
								+ " already has marks entered for exam schedule " + examSchedule.getId());
					});
			return marksEntryRepository.save(
					new MarksEntry(examSchedule, mark.enrollment(), mark.marksObtained(), mark.absent(), mark.remarks()));
		}).toList();
		recomputeSubjectRanksIfEnabled(examSchedule);
		return entered;
	}

	/** DRAFT -> SUBMITTED. Creates the register (DRAFT) first if marks were never entered. */
	@Transactional
	public MarksEntryRegister submitMarksRegister(ExamSchedule examSchedule) {
		MarksEntryRegister register = registerFor(examSchedule);
		register.submit();
		MarksEntryRegister saved = marksEntryRegisterRepository.save(register);
		auditLogService.record("MarksEntryRegister", saved.getId(), "SUBMITTED", MarksEntryRegisterStatus.DRAFT, MarksEntryRegisterStatus.SUBMITTED);
		return saved;
	}

	/** SUBMITTED -> APPROVED. */
	@Transactional
	public MarksEntryRegister approveMarksRegister(ExamSchedule examSchedule) {
		MarksEntryRegister register = marksEntryRegisterRepository.findByExamScheduleId(examSchedule.getId())
				.orElseThrow(() -> new IllegalStateException("No marks entry register exists yet for schedule " + examSchedule.getId()));
		register.approve();
		MarksEntryRegister saved = marksEntryRegisterRepository.save(register);
		auditLogService.record("MarksEntryRegister", saved.getId(), "APPROVED", MarksEntryRegisterStatus.SUBMITTED, MarksEntryRegisterStatus.APPROVED);
		return saved;
	}

	private MarksEntryRegister registerFor(ExamSchedule examSchedule) {
		return marksEntryRegisterRepository.findByExamScheduleId(examSchedule.getId())
				.orElseGet(() -> marksEntryRegisterRepository.save(new MarksEntryRegister(examSchedule)));
	}

	/**
	 * Pre-publish correction. Rejects with a pointer to correctMarksAfterPublish() if a
	 * PUBLISHED report card already exists for this entry's (exam, enrollment) - see #138.
	 */
	@Transactional
	public MarksEntry correctMarks(MarksEntry marksEntry, Double marksObtained, boolean absent, String remarks) {
		if (currentPublishedReportCard(marksEntry).isPresent()) {
			throw new IllegalStateException(
					"A report card is already published for this student's exam - use correctMarksAfterPublish() instead");
		}
		return applyCorrection(marksEntry, marksObtained, absent, remarks, "CORRECTED");
	}

	/**
	 * Post-publish correction (#138) - requires a PUBLISHED report card to exist, and flags
	 * it stale rather than silently desyncing it. Gated behind MARKS_CORRECT_AFTER_PUBLISH
	 * at the controller, a distinct, higher-trust permission from ordinary MARKS_CORRECT.
	 */
	@Transactional
	public MarksEntry correctMarksAfterPublish(MarksEntry marksEntry, Double marksObtained, boolean absent, String remarks) {
		ReportCard published = currentPublishedReportCard(marksEntry)
				.orElseThrow(() -> new IllegalStateException("No published report card exists for this student's exam - use correctMarks() instead"));
		MarksEntry corrected = applyCorrection(marksEntry, marksObtained, absent, remarks, "CORRECTED_AFTER_PUBLISH");
		published.markStale();
		reportCardRepository.save(published);
		return corrected;
	}

	private Optional<ReportCard> currentPublishedReportCard(MarksEntry marksEntry) {
		return reportCardRepository.findByExamIdAndStudentEnrollmentIdAndStatus(
				marksEntry.getExamSchedule().getExam().getId(), marksEntry.getStudentEnrollment().getId(), ReportCardStatus.PUBLISHED);
	}

	private MarksEntry applyCorrection(MarksEntry marksEntry, Double marksObtained, boolean absent, String remarks, String action) {
		Double previousMarks = marksEntry.getMarksObtained();
		marksEntry.correctMarks(marksObtained, absent, remarks);
		MarksEntry saved = marksEntryRepository.save(marksEntry);
		auditLogService.record("MarksEntry", marksEntry.getId(), action, previousMarks, saved.getMarksObtained());
		recomputeSubjectRanksIfEnabled(saved.getExamSchedule());
		return saved;
	}

	/**
	 * Requires every ExamSchedule applicable to the student's section to have an APPROVED
	 * MarksEntryRegister (#134) and a MarksEntry (#139-aware: whole-class or this exact
	 * section). Rejects a duplicate publish - unless the existing PUBLISHED report card is
	 * stale (#138), in which case it's superseded and a fresh one is published in its place
	 * (a republish). Computes overall pass/fail per the org's configured strategy (#135) and,
	 * if ranking is enabled, recomputes class rank for the whole cohort afterward (#136).
	 */
	@Transactional
	public ReportCard publishReportCard(Exam exam, StudentEnrollment studentEnrollment, GradingScale gradingScale) {
		Optional<ReportCard> existingPublished = reportCardRepository.findByExamIdAndStudentEnrollmentIdAndStatus(
				exam.getId(), studentEnrollment.getId(), ReportCardStatus.PUBLISHED);
		if (existingPublished.isPresent() && !existingPublished.get().isStale()) {
			throw new IllegalStateException(
					"Report card already published for exam " + exam.getId() + " and enrollment " + studentEnrollment.getId());
		}

		SchoolClass schoolClass = studentEnrollment.getSection().getSchoolClass();
		List<ExamSchedule> schedules =
				examScheduleRepository.findApplicableToSection(exam.getId(), schoolClass.getId(), studentEnrollment.getSection().getId());
		if (schedules.isEmpty()) {
			throw new IllegalStateException("No exam schedules found for exam " + exam.getId() + " and class " + schoolClass.getId());
		}

		double totalObtained = 0;
		double totalMax = 0;
		boolean everySubjectPassed = true;
		for (ExamSchedule schedule : schedules) {
			MarksEntryRegister register = marksEntryRegisterRepository.findByExamScheduleId(schedule.getId())
					.orElseThrow(() -> new IllegalStateException(
							"Marks for subject " + schedule.getSubject().getId() + " have not been entered yet - cannot publish report card"));
			if (register.getRegisterStatus() != MarksEntryRegisterStatus.APPROVED) {
				throw new IllegalStateException("Marks for subject " + schedule.getSubject().getId() + " are not yet approved ("
						+ register.getRegisterStatus() + ") - cannot publish report card");
			}
			MarksEntry entry = marksEntryRepository.findByExamScheduleIdAndStudentEnrollmentId(schedule.getId(), studentEnrollment.getId())
					.orElseThrow(() -> new IllegalStateException(
							"Marks missing for subject " + schedule.getSubject().getId() + " - cannot publish report card"));
			totalObtained += entry.getMarksObtained();
			totalMax += schedule.getMaxMarks();
			if (entry.isAbsent() || entry.getMarksObtained() < schedule.getPassMarks()) {
				everySubjectPassed = false;
			}
		}

		double percentage = totalMax == 0 ? 0 : (totalObtained / totalMax) * 100;
		String grade = resolveGrade(gradingScale, percentage);
		boolean passed = resolveOverallPassed(everySubjectPassed, percentage);

		if (existingPublished.isPresent()) {
			ReportCard stale = existingPublished.get();
			stale.supersede();
			reportCardRepository.save(stale);
			auditLogService.record("ReportCard", stale.getId(), "SUPERSEDED", null, null);
		}

		ReportCard saved = reportCardRepository.save(new ReportCard(exam, studentEnrollment, totalObtained, totalMax, percentage, grade, passed));
		auditLogService.record("ReportCard", saved.getId(), existingPublished.isPresent() ? "REPUBLISHED" : "PUBLISHED", null, saved.getPercentage());
		recomputeClassRanksIfEnabled(exam, schoolClass);
		return saved;
	}

	private boolean resolveOverallPassed(boolean everySubjectPassed, double percentage) {
		ExaminationSettings settings = currentSettings();
		return switch (settings.getPassFailStrategy()) {
			case PASS_EVERY_SUBJECT -> everySubjectPassed;
			case MINIMUM_AGGREGATE_PERCENTAGE -> percentage >= settings.getMinimumAggregatePercentage();
			case BOTH -> everySubjectPassed && percentage >= settings.getMinimumAggregatePercentage();
		};
	}

	private void recomputeSubjectRanksIfEnabled(ExamSchedule examSchedule) {
		if (!currentSettings().isRankingEnabled()) {
			return;
		}
		List<MarksEntry> ranked = marksEntryRepository.findByExamScheduleId(examSchedule.getId()).stream()
				.sorted(Comparator.comparingDouble(MarksEntry::getMarksObtained).reversed())
				.toList();
		assignCompetitionRanks(ranked, MarksEntry::getMarksObtained, MarksEntry::assignRank);
		marksEntryRepository.saveAll(ranked);
	}

	private void recomputeClassRanksIfEnabled(Exam exam, SchoolClass schoolClass) {
		if (!currentSettings().isRankingEnabled()) {
			return;
		}
		List<ReportCard> ranked = reportCardRepository
				.findByExamIdAndStudentEnrollment_Section_SchoolClass_IdAndStatus(exam.getId(), schoolClass.getId(), ReportCardStatus.PUBLISHED)
				.stream()
				.sorted(Comparator.comparingDouble(ReportCard::getPercentage).reversed())
				.toList();
		assignCompetitionRanks(ranked, ReportCard::getPercentage, ReportCard::assignClassRank);
		reportCardRepository.saveAll(ranked);
	}

	/** Standard competition ranking (1,2,2,4): equal scores get equal rank, the next rank skips - a documented, non-arbitrary tie rule. */
	private <T> void assignCompetitionRanks(List<T> descendingSorted, ToDoubleFunction<T> scoreOf, RankAssigner<T> rankSetter) {
		int rank = 0;
		int position = 0;
		Double previousScore = null;
		for (T item : descendingSorted) {
			position++;
			double score = scoreOf.applyAsDouble(item);
			if (previousScore == null || score != previousScore) {
				rank = position;
			}
			rankSetter.assign(item, rank);
			previousScore = score;
		}
	}

	private interface RankAssigner<T> {
		void assign(T item, int rank);
	}

	private ExaminationSettings currentSettings() {
		return examinationSettingsRepository.findByOrganisationId(TenantContext.getOrganisationId()).orElseGet(ExaminationSettings::new);
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
