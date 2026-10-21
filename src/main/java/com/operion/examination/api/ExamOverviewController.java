package com.operion.examination.api;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.operion.academic.SchoolClass;
import com.operion.authorization.RequirePermission;
import com.operion.examination.Exam;
import com.operion.examination.ExamRepository;
import com.operion.examination.ExamSchedule;
import com.operion.examination.ExamScheduleRepository;
import com.operion.examination.MarksEntryRegister;
import com.operion.examination.MarksEntryRegisterRepository;
import com.operion.examination.MarksEntryRegisterStatus;
import com.operion.examination.ReportCard;
import com.operion.examination.ReportCardRepository;
import com.operion.examination.ReportCardStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only rollup behind the Examinations overview. Aggregates in memory - one academic year holds a handful of
 * exams and at most a few hundred schedules, and every figure must agree with the registers and report cards the
 * exam detail pages already show. */
@RestController
@RequestMapping("/api/v1/examinations/overview")
@RequirePermission("EXAM_VIEW")
public class ExamOverviewController {

	private static final int UPCOMING_LIMIT = 5;

	private final ExamRepository examRepository;
	private final ExamScheduleRepository examScheduleRepository;
	private final MarksEntryRegisterRepository marksEntryRegisterRepository;
	private final ReportCardRepository reportCardRepository;

	public ExamOverviewController(ExamRepository examRepository, ExamScheduleRepository examScheduleRepository,
			MarksEntryRegisterRepository marksEntryRegisterRepository, ReportCardRepository reportCardRepository) {
		this.examRepository = examRepository;
		this.examScheduleRepository = examScheduleRepository;
		this.marksEntryRegisterRepository = marksEntryRegisterRepository;
		this.reportCardRepository = reportCardRepository;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public ExamOverviewResponse overview(@RequestParam Long academicYearId) {
		LocalDate today = LocalDate.now();
		List<Exam> exams = examRepository.findByAcademicYearId(academicYearId);
		List<ExamSchedule> schedules = examScheduleRepository.findForOverview(academicYearId);
		List<ReportCard> cards = reportCardRepository.findForOverview(academicYearId, ReportCardStatus.PUBLISHED);

		Map<Long, MarksEntryRegisterStatus> registerBySchedule = new HashMap<>();
		for (MarksEntryRegister register : marksEntryRegisterRepository.findForOverview(academicYearId)) {
			registerBySchedule.put(register.getExamSchedule().getId(), register.getRegisterStatus());
		}
		Map<Long, List<ExamSchedule>> schedulesByExam = new HashMap<>();
		for (ExamSchedule schedule : schedules) {
			schedulesByExam.computeIfAbsent(schedule.getExam().getId(), key -> new ArrayList<>()).add(schedule);
		}
		Map<Long, List<ReportCard>> cardsByExam = new HashMap<>();
		for (ReportCard card : cards) {
			cardsByExam.computeIfAbsent(card.getExam().getId(), key -> new ArrayList<>()).add(card);
		}

		List<ExamOverviewResponse.ExamRow> rows = exams.stream()
				.map(exam -> examRow(exam, schedulesByExam.getOrDefault(exam.getId(), List.of()), registerBySchedule, cardsByExam.containsKey(exam.getId())))
				.sorted(Comparator.comparing(ExamOverviewResponse.ExamRow::startDate, Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();

		List<ExamOverviewResponse.ExamRow> upcomingExams = rows.stream().filter(row -> row.startDate() != null && !row.startDate().isBefore(today)).toList();
		ExamOverviewResponse.NextExam next = upcomingExams.isEmpty() ? null
				: new ExamOverviewResponse.NextExam(upcomingExams.get(0).id(), upcomingExams.get(0).name(), upcomingExams.get(0).startDate());

		// Marks are "pending" once the paper's date has passed and its register still isn't submitted.
		List<ExamSchedule> pending = schedules.stream()
				.filter(s -> !s.getExamDate().isAfter(today) && !isEntered(registerBySchedule.get(s.getId()))).toList();

		ExamOverviewResponse.PublishedExam lastPublished = rows.stream().filter(row -> "PUBLISHED".equals(row.resultStatus()))
				.max(Comparator.comparing(ExamOverviewResponse.ExamRow::endDate, Comparator.nullsFirst(Comparator.naturalOrder())))
				.map(row -> new ExamOverviewResponse.PublishedExam(row.id(), row.name(), row.endDate())).orElse(null);

		List<ExamOverviewResponse.UpcomingSchedule> upcoming = schedules.stream().filter(s -> !s.getExamDate().isBefore(today))
				.sorted(Comparator.comparing(ExamSchedule::getExamDate)).limit(UPCOMING_LIMIT)
				.map(s -> new ExamOverviewResponse.UpcomingSchedule(s.getExam().getId(), s.getId(), s.getExam().getName(), s.getExamDate(),
						s.getSchoolClass().label(), s.getSubject().getName()))
				.toList();

		return new ExamOverviewResponse(exams.size(), upcomingExams.size(), next, pending.size(),
				(int) pending.stream().map(s -> s.getSchoolClass().getId()).distinct().count(),
				(int) rows.stream().filter(row -> "PUBLISHED".equals(row.resultStatus())).count(), lastPublished, rows, upcoming,
				classProgress(schedules, registerBySchedule), resultSummary(cards));
	}

	private static boolean isEntered(MarksEntryRegisterStatus status) {
		return status == MarksEntryRegisterStatus.SUBMITTED || status == MarksEntryRegisterStatus.APPROVED;
	}

	private static int percent(long part, long whole) {
		return whole == 0 ? 0 : (int) Math.round(part * 100.0 / whole);
	}

	private ExamOverviewResponse.ExamRow examRow(Exam exam, List<ExamSchedule> schedules, Map<Long, MarksEntryRegisterStatus> registers, boolean published) {
		long entered = schedules.stream().filter(s -> isEntered(registers.get(s.getId()))).count();
		String resultStatus = published ? "PUBLISHED" : schedules.isEmpty() ? "DRAFT" : entered > 0 ? "IN_PROGRESS" : "NOT_STARTED";
		LocalDate start = schedules.stream().map(ExamSchedule::getExamDate).min(Comparator.naturalOrder()).orElse(null);
		LocalDate end = schedules.stream().map(ExamSchedule::getExamDate).max(Comparator.naturalOrder()).orElse(null);
		List<String> classLabels = schedules.stream().map(ExamSchedule::getSchoolClass).distinct()
				.sorted(Comparator.comparingInt((SchoolClass c) -> c.getGradeLevel().getSequenceOrder())).map(SchoolClass::label).toList();
		String classes = classLabels.isEmpty() ? "" : classLabels.size() == 1 ? classLabels.get(0) : classLabels.get(0) + " – " + classLabels.get(classLabels.size() - 1);
		return new ExamOverviewResponse.ExamRow(exam.getId(), exam.getName(), exam.getExamType().name(), exam.getStatus().name(), classes, start, end,
				schedules.size(), percent(entered, schedules.size()), resultStatus);
	}

	private List<ExamOverviewResponse.ClassProgress> classProgress(List<ExamSchedule> schedules, Map<Long, MarksEntryRegisterStatus> registers) {
		Map<SchoolClass, List<ExamSchedule>> byClass = new LinkedHashMap<>();
		schedules.stream().sorted(Comparator.comparingInt((ExamSchedule s) -> s.getSchoolClass().getGradeLevel().getSequenceOrder()).reversed())
				.forEach(s -> byClass.computeIfAbsent(s.getSchoolClass(), key -> new ArrayList<>()).add(s));
		return byClass.entrySet().stream().map(entry -> new ExamOverviewResponse.ClassProgress(entry.getKey().getId(), entry.getKey().label(),
				percent(entry.getValue().stream().filter(s -> isEntered(registers.get(s.getId()))).count(), entry.getValue().size()))).toList();
	}

	private ExamOverviewResponse.ResultSummary resultSummary(List<ReportCard> cards) {
		if (cards.isEmpty()) {
			return new ExamOverviewResponse.ResultSummary(null, 0, List.of());
		}
		Map<String, Integer> byGrade = new HashMap<>();
		Map<String, Double> percentageSum = new HashMap<>();
		for (ReportCard card : cards) {
			String grade = card.getOverallGrade() == null ? "—" : card.getOverallGrade();
			byGrade.merge(grade, 1, Integer::sum);
			percentageSum.merge(grade, card.getPercentage() == null ? 0.0 : card.getPercentage(), Double::sum);
		}
		// Best grade first: ordered by the average percentage of the cards that earned it, so no grade-scale lookup is needed.
		List<ExamOverviewResponse.GradeCount> grades = byGrade.entrySet().stream()
				.sorted(Comparator.comparingDouble((Map.Entry<String, Integer> entry) -> percentageSum.get(entry.getKey()) / entry.getValue()).reversed())
				.map(entry -> new ExamOverviewResponse.GradeCount(entry.getKey(), entry.getValue())).toList();
		return new ExamOverviewResponse.ResultSummary(percent(cards.stream().filter(ReportCard::isPassed).count(), cards.size()), cards.size(), grades);
	}
}
