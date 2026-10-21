package com.operion.performance;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.attendance.StaffAttendance;
import com.operion.attendance.StaffAttendanceRepository;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The administrator's view across a whole appraisal cycle: who is reviewed and who is still pending, the scores
 * the existing scorecard produces for the cycle's dates, and the roll-ups behind the insights. It adds no scoring
 * of its own - scores come from {@link KpiService}, ratings and goals from the stored rows.
 *
 * <p>The cycle model has no draft/submit/approve stages, so a review is "completed" exactly when a MANAGER rating
 * exists for the teacher in that cycle, and it is "final" once the cycle is closed (ratings can no longer change).
 * A cycle has no stored roster either: the staff in it are the teachers with an active class assignment now, plus
 * anyone who already has a rating or goal in the cycle.
 */
@Service
public class PerformanceOverviewService {

	static final String NO_DEPARTMENT = "No department assigned";

	public enum ReviewStatus {
		/** Open cycle, no manager rating yet. */
		PENDING,
		/** Open cycle, a manager rating is recorded. */
		REVIEWED,
		/** Closed cycle, reviewed - nothing can change any more. */
		FINALIZED,
		/** Closed cycle that ended without a manager rating. */
		NOT_REVIEWED;

		public boolean completed() {
			return this == REVIEWED || this == FINALIZED;
		}
	}

	public record StaffRow(Long personId, String name, String employeeCode, String designation, String department, Double presencePercent,
			Double overallScore, ReviewStatus reviewStatus, Double managerRating, boolean selfAppraisalSubmitted, int goalCount,
			int goalsAchieved, Instant lastUpdated) {
	}

	/** {@code averageScore} covers completed reviews that have a score; staff without a score are left out, never counted as zero. */
	public record Summary(int totalStaff, int pending, int completed, Double averageScore, int scoredCount, Double averageManagerRating) {
	}

	public record DepartmentScore(String department, int staff, int scored, Double averageScore) {
	}

	public record GoalStats(int total, int open, int achieved, int partlyAchieved, int missed) {
	}

	public record PresencePoint(String month, Double presencePercent, int sample) {
	}

	/** {@code managerRatingCounts} has one entry per rating 1..5, counting MANAGER ratings only so sources are not mixed. */
	public record Insights(List<DepartmentScore> departments, List<Integer> managerRatingCounts, GoalStats goals, List<PresencePoint> presenceTrend) {
	}

	public record Overview(AppraisalService.CycleView cycle, boolean metricsConfigured, Double presenceTarget, Summary summary,
			List<StaffRow> staff, Insights insights) {
	}

	/** The identity and presence block at the top of one teacher's appraisal. */
	public record StaffInfo(String name, String employeeCode, String designation, String department, Double presencePercent,
			int presenceSample, Double presenceTarget, ReviewStatus reviewStatus, Double managerRating) {
	}

	public record CycleSummary(AppraisalService.CycleView cycle, int staffCount, int completed, int pending) {
	}

	private final AppraisalCycleRepository cycleRepository;
	private final TeacherFeedbackRepository feedbackRepository;
	private final TeacherGoalRepository goalRepository;
	private final TeacherAssignmentRepository assignmentRepository;
	private final StaffProfileRepository staffProfileRepository;
	private final StaffAttendanceRepository attendanceRepository;
	private final PersonRepository personRepository;
	private final KpiService kpiService;
	private final StaffAttendanceKpiProvider attendanceProvider;

	public PerformanceOverviewService(AppraisalCycleRepository cycleRepository, TeacherFeedbackRepository feedbackRepository,
			TeacherGoalRepository goalRepository, TeacherAssignmentRepository assignmentRepository,
			StaffProfileRepository staffProfileRepository, StaffAttendanceRepository attendanceRepository,
			PersonRepository personRepository, KpiService kpiService, StaffAttendanceKpiProvider attendanceProvider) {
		this.cycleRepository = cycleRepository;
		this.feedbackRepository = feedbackRepository;
		this.goalRepository = goalRepository;
		this.assignmentRepository = assignmentRepository;
		this.staffProfileRepository = staffProfileRepository;
		this.attendanceRepository = attendanceRepository;
		this.personRepository = personRepository;
		this.kpiService = kpiService;
		this.attendanceProvider = attendanceProvider;
	}

	@Transactional(readOnly = true)
	public Overview overview(Long cycleId) {
		AppraisalCycle cycle = cycle(cycleId);
		KpiService.Scorecard scorecard = kpiService.scorecard(cycle.getStartDate(), cycle.getEndDate());
		List<TeacherFeedback> feedback = feedbackRepository.findByCycleIdWithTeacher(cycleId);
		List<TeacherGoal> goals = goalRepository.findByCycleIdWithTeacher(cycleId);

		Map<Long, Person> roster = new LinkedHashMap<>();
		scorecard.teachers().forEach(t -> roster.put(t.person().getId(), t.person()));
		feedback.forEach(f -> roster.putIfAbsent(f.getTeacher().getId(), f.getTeacher()));
		goals.forEach(g -> roster.putIfAbsent(g.getTeacher().getId(), g.getTeacher()));

		Map<Long, Double> scores = new HashMap<>();
		scorecard.teachers().forEach(t -> {
			if (t.overallScore() != null) {
				scores.put(t.person().getId(), t.overallScore());
			}
		});
		Map<Long, MetricValue> presence = attendanceProvider.compute(KpiMetric.STAFF_PRESENCE,
				new KpiPeriod(cycle.getStartDate(), cycle.getEndDate()), null);
		Map<Long, StaffProfile> profiles = staffProfileRepository.findAll().stream()
				.collect(Collectors.toMap(p -> p.getPerson().getId(), p -> p, (a, b) -> a));
		Map<Long, List<TeacherFeedback>> feedbackByTeacher = feedback.stream().collect(Collectors.groupingBy(f -> f.getTeacher().getId()));
		Map<Long, List<TeacherGoal>> goalsByTeacher = goals.stream().collect(Collectors.groupingBy(g -> g.getTeacher().getId()));

		List<StaffRow> rows = roster.values().stream()
				.map(person -> row(cycle, person, profiles.get(person.getId()), scores.get(person.getId()),
						presence.get(person.getId()), feedbackByTeacher.getOrDefault(person.getId(), List.of()),
						goalsByTeacher.getOrDefault(person.getId(), List.of())))
				.sorted(Comparator.comparing(StaffRow::name, String.CASE_INSENSITIVE_ORDER)).toList();

		return new Overview(AppraisalService.CycleView.of(cycle), !scorecard.metrics().isEmpty(), presenceTarget(), summary(rows),
				rows, insights(cycle, rows, feedback, goals, roster.keySet()));
	}

	/** One teacher's identity and presence for a cycle; staff without a staff profile still get a name. */
	@Transactional(readOnly = true)
	public StaffInfo staffInfo(Long cycleId, Long personId) {
		AppraisalCycle cycle = cycle(cycleId);
		Person person = personRepository.findById(personId).orElseThrow(() -> new IllegalArgumentException("No person with id " + personId));
		StaffProfile profile = staffProfileRepository.findByPersonId(personId).orElse(null);
		MetricValue presence = attendanceProvider
				.compute(KpiMetric.STAFF_PRESENCE, new KpiPeriod(cycle.getStartDate(), cycle.getEndDate()), null).get(personId);
		List<TeacherFeedback> given = feedbackRepository.findByCycleIdAndTeacherId(cycleId, personId);
		return new StaffInfo(displayName(person), profile == null ? null : profile.getEmployeeCode(),
				profile == null ? null : profile.getDesignation().getName(),
				profile == null || profile.getDepartment() == null ? null : profile.getDepartment().getName(),
				presence == null ? null : presence.value(), presence == null ? 0 : presence.sample(), presenceTarget(),
				status(cycle, managerRating(given) != null), managerRating(given));
	}

	@Transactional(readOnly = true)
	public List<CycleSummary> cycleSummaries() {
		Set<Long> activeTeachers = assignmentRepository.findByStatus(TeacherAssignmentStatus.ACTIVE).stream()
				.map(TeacherAssignment::getTeacherPerson).map(Person::getId).collect(Collectors.toSet());
		Map<Long, Set<Long>> ratedByCycle = new HashMap<>();
		Map<Long, Set<Long>> managerRatedByCycle = new HashMap<>();
		for (TeacherFeedbackRepository.RatedTeacher rated : feedbackRepository.findAllRatedTeachers()) {
			ratedByCycle.computeIfAbsent(rated.cycleId(), k -> new HashSet<>()).add(rated.teacherId());
			if (rated.source() == FeedbackSource.MANAGER) {
				managerRatedByCycle.computeIfAbsent(rated.cycleId(), k -> new HashSet<>()).add(rated.teacherId());
			}
		}
		return cycleRepository.findAllByOrderByStartDateDescIdDesc().stream().map(cycle -> {
			Set<Long> staff = new HashSet<>(activeTeachers);
			staff.addAll(ratedByCycle.getOrDefault(cycle.getId(), Set.of()));
			int completed = managerRatedByCycle.getOrDefault(cycle.getId(), Set.of()).size();
			return new CycleSummary(AppraisalService.CycleView.of(cycle), staff.size(), completed, staff.size() - completed);
		}).toList();
	}

	private StaffRow row(AppraisalCycle cycle, Person person, StaffProfile profile, Double score, MetricValue presence,
			List<TeacherFeedback> feedback, List<TeacherGoal> goals) {
		Double manager = managerRating(feedback);
		Instant lastUpdated = Stream.concat(feedback.stream().map(TeacherFeedback::getUpdatedAt), goals.stream().map(TeacherGoal::getUpdatedAt))
				.filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
		return new StaffRow(person.getId(), displayName(person), profile == null ? null : profile.getEmployeeCode(),
				profile == null ? null : profile.getDesignation().getName(),
				profile == null || profile.getDepartment() == null ? null : profile.getDepartment().getName(),
				presence == null ? null : presence.value(), score, status(cycle, manager != null), manager,
				feedback.stream().anyMatch(f -> f.getSource() == FeedbackSource.SELF), goals.size(),
				(int) goals.stream().filter(g -> g.getStatus() == GoalStatus.ACHIEVED).count(), lastUpdated);
	}

	private static Summary summary(List<StaffRow> rows) {
		int completed = (int) rows.stream().filter(r -> r.reviewStatus().completed()).count();
		List<Double> scored = rows.stream().filter(r -> r.reviewStatus().completed() && r.overallScore() != null).map(StaffRow::overallScore).toList();
		return new Summary(rows.size(), rows.size() - completed, completed, average(scored), scored.size(),
				average(rows.stream().map(StaffRow::managerRating).filter(Objects::nonNull).toList()));
	}

	private Insights insights(AppraisalCycle cycle, List<StaffRow> rows, List<TeacherFeedback> feedback, List<TeacherGoal> goals,
			Set<Long> rosterIds) {
		// Departments share one set of metrics, weights and targets, so their averages are on the same scale.
		Map<String, List<StaffRow>> byDepartment = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		rows.forEach(r -> byDepartment.computeIfAbsent(r.department() == null ? NO_DEPARTMENT : r.department(), k -> new ArrayList<>()).add(r));
		List<DepartmentScore> departments = byDepartment.entrySet().stream().map(e -> {
			List<Double> scored = e.getValue().stream().map(StaffRow::overallScore).filter(Objects::nonNull).toList();
			return new DepartmentScore(e.getKey(), e.getValue().size(), scored.size(), average(scored));
		}).toList();

		int[] counts = new int[TeacherFeedback.MAX_RATING];
		feedback.stream().filter(f -> f.getSource() == FeedbackSource.MANAGER).forEach(f -> counts[f.getRating() - 1]++);
		List<Integer> distribution = Arrays.stream(counts).boxed().toList();

		GoalStats goalStats = new GoalStats(goals.size(), count(goals, GoalStatus.OPEN), count(goals, GoalStatus.ACHIEVED),
				count(goals, GoalStatus.PARTLY_ACHIEVED), count(goals, GoalStatus.MISSED));
		return new Insights(departments, distribution, goalStats, presenceTrend(cycle, rosterIds));
	}

	/** Monthly presence for the cycle's staff, computed the same way as the presence metric and kept apart from the score. */
	private List<PresencePoint> presenceTrend(AppraisalCycle cycle, Set<Long> rosterIds) {
		Map<YearMonth, int[]> tally = new TreeMap<>();
		for (StaffAttendance a : attendanceRepository.findByAttendanceDateBetween(cycle.getStartDate(), cycle.getEndDate())) {
			if (!rosterIds.contains(a.getPerson().getId())) {
				continue;
			}
			boolean attended = StaffAttendanceKpiProvider.attended(a.getAttendanceStatus());
			if (attended || StaffAttendanceKpiProvider.isAbsent(a.getAttendanceStatus())) {
				int[] counts = tally.computeIfAbsent(YearMonth.from(a.getAttendanceDate()), k -> new int[2]);
				counts[1]++;
				if (attended) {
					counts[0]++;
				}
			}
		}
		return tally.entrySet().stream().map(e -> new PresencePoint(e.getKey().toString(), 100.0 * e.getValue()[0] / e.getValue()[1], e.getValue()[1])).toList();
	}

	/** The configured presence target, or null when the presence metric is not being tracked (no target to speak of). */
	private Double presenceTarget() {
		return kpiService.definitions().stream().filter(d -> d.metric() == KpiMetric.STAFF_PRESENCE && d.enabled()).findFirst()
				.map(d -> d.target().doubleValue()).orElse(null);
	}

	static ReviewStatus status(AppraisalCycle cycle, boolean managerRated) {
		if (cycle.isOpen()) {
			return managerRated ? ReviewStatus.REVIEWED : ReviewStatus.PENDING;
		}
		return managerRated ? ReviewStatus.FINALIZED : ReviewStatus.NOT_REVIEWED;
	}

	private static Double managerRating(List<TeacherFeedback> feedback) {
		return average(feedback.stream().filter(f -> f.getSource() == FeedbackSource.MANAGER).map(f -> (double) f.getRating()).toList());
	}

	private static Double average(List<Double> values) {
		return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
	}

	private static int count(List<TeacherGoal> goals, GoalStatus status) {
		return (int) goals.stream().filter(g -> g.getStatus() == status).count();
	}

	private static String displayName(Person person) {
		String name = ((person.getFirstName() == null ? "" : person.getFirstName()) + " " + (person.getLastName() == null ? "" : person.getLastName())).trim();
		return name.isEmpty() ? "Unnamed staff member" : name;
	}

	private AppraisalCycle cycle(Long id) {
		return cycleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No appraisal cycle with id " + id));
	}
}
