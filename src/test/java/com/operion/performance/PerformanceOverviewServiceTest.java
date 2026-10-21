package com.operion.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.attendance.AttendanceStatus;
import com.operion.attendance.StaffAttendance;
import com.operion.attendance.StaffAttendanceRepository;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Department;
import com.operion.organisation.Designation;
import com.operion.performance.PerformanceOverviewService.ReviewStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** What the dashboard reports for a cycle, with every repository mocked: statuses, averages, grouping and the cycle boundary. */
class PerformanceOverviewServiceTest {

	private final AppraisalCycleRepository cycleRepository = mock(AppraisalCycleRepository.class);
	private final TeacherFeedbackRepository feedbackRepository = mock(TeacherFeedbackRepository.class);
	private final TeacherGoalRepository goalRepository = mock(TeacherGoalRepository.class);
	private final TeacherAssignmentRepository assignmentRepository = mock(TeacherAssignmentRepository.class);
	private final StaffProfileRepository staffProfileRepository = mock(StaffProfileRepository.class);
	private final StaffAttendanceRepository attendanceRepository = mock(StaffAttendanceRepository.class);
	private final PersonRepository personRepository = mock(PersonRepository.class);
	private final KpiService kpiService = mock(KpiService.class);
	private final StaffAttendanceKpiProvider attendanceProvider = mock(StaffAttendanceKpiProvider.class);
	private final PerformanceOverviewService service = new PerformanceOverviewService(cycleRepository, feedbackRepository, goalRepository,
			assignmentRepository, staffProfileRepository, attendanceRepository, personRepository, kpiService, attendanceProvider);

	private final LocalDate start = LocalDate.of(2026, 6, 1);
	private final LocalDate end = LocalDate.of(2026, 9, 30);
	private final AppraisalCycle cycle = new AppraisalCycle("Term 1", start, end);
	private final Person asha = person(1, "Asha", "Nair");
	private final Person ben = person(2, "Ben", "Kurian");
	private final Person chitra = person(3, "Chitra", "Rao");
	private final Person reviewer = person(9, "Head", "Teacher");

	private static Person person(long id, String first, String last) {
		Person person = mock(Person.class);
		when(person.getId()).thenReturn(id);
		when(person.getFirstName()).thenReturn(first);
		when(person.getLastName()).thenReturn(last);
		return person;
	}

	private static KpiService.TeacherRow scored(Person person, Double score) {
		return new KpiService.TeacherRow(person, score, List.of());
	}

	private static StaffProfile profile(Person person, String code, String designation, String department) {
		StaffProfile profile = mock(StaffProfile.class);
		Designation d = mock(Designation.class);
		when(d.getName()).thenReturn(designation);
		when(profile.getPerson()).thenReturn(person);
		when(profile.getEmployeeCode()).thenReturn(code);
		when(profile.getDesignation()).thenReturn(d);
		if (department != null) {
			Department dept = mock(Department.class);
			when(dept.getName()).thenReturn(department);
			when(profile.getDepartment()).thenReturn(dept);
		}
		return profile;
	}

	private void scorecard(KpiService.TeacherRow... rows) {
		KpiService.Definition metric = new KpiService.Definition(KpiMetric.STAFF_PRESENCE, true, 1, BigDecimal.valueOf(95), null);
		when(kpiService.scorecard(start, end)).thenReturn(new KpiService.Scorecard(start, end, List.of(metric), List.of(rows)));
		when(kpiService.definitions()).thenReturn(List.of(metric));
	}

	@BeforeEach
	void wire() {
		ReflectionTestUtils.setField(cycle, "id", 1L);
		when(cycleRepository.findById(1L)).thenReturn(Optional.of(cycle));
		when(attendanceProvider.compute(any(), any(), any())).thenReturn(Map.of());
		when(staffProfileRepository.findAll()).thenReturn(List.of());
		when(feedbackRepository.findByCycleIdWithTeacher(1L)).thenReturn(List.of());
		when(goalRepository.findByCycleIdWithTeacher(1L)).thenReturn(List.of());
		when(attendanceRepository.findByAttendanceDateBetween(any(), any())).thenReturn(List.of());
	}

	@Test
	void aReviewIsCompleteOnlyWithAManagerRatingAndFinalOnlyOnceTheCycleCloses() {
		assertThat(PerformanceOverviewService.status(cycle, false)).isEqualTo(ReviewStatus.PENDING);
		assertThat(PerformanceOverviewService.status(cycle, true)).isEqualTo(ReviewStatus.REVIEWED);
		cycle.close();
		assertThat(PerformanceOverviewService.status(cycle, true)).isEqualTo(ReviewStatus.FINALIZED);
		assertThat(PerformanceOverviewService.status(cycle, false)).isEqualTo(ReviewStatus.NOT_REVIEWED);
	}

	@Test
	void aSelfAppraisalAloneLeavesTheReviewPending() {
		scorecard(scored(asha, 70.0));
		when(feedbackRepository.findByCycleIdWithTeacher(1L))
				.thenReturn(List.of(new TeacherFeedback(cycle, asha, asha, FeedbackSource.SELF, 4, "ok")));

		var overview = service.overview(1L);

		assertThat(overview.staff()).singleElement().satisfies(row -> {
			assertThat(row.reviewStatus()).isEqualTo(ReviewStatus.PENDING);
			assertThat(row.selfAppraisalSubmitted()).isTrue();
			assertThat(row.managerRating()).isNull();
		});
		assertThat(overview.summary().pending()).isEqualTo(1);
		assertThat(overview.summary().completed()).isZero();
		assertThat(overview.summary().averageScore()).isNull();
	}

	@Test
	void theAverageCoversCompletedReviewsWithAScoreAndNeverCountsAMissingScoreAsZero() {
		scorecard(scored(asha, 80.0), scored(ben, null), scored(chitra, 40.0));
		when(feedbackRepository.findByCycleIdWithTeacher(1L)).thenReturn(List.of(
				new TeacherFeedback(cycle, asha, reviewer, FeedbackSource.MANAGER, 4, null),
				new TeacherFeedback(cycle, ben, reviewer, FeedbackSource.MANAGER, 2, null)));

		var summary = service.overview(1L).summary();

		assertThat(summary.totalStaff()).isEqualTo(3);
		assertThat(summary.completed()).isEqualTo(2);
		assertThat(summary.pending()).isEqualTo(1);
		// Ben is complete but has no score and Chitra has a score but no review: only Asha counts.
		assertThat(summary.averageScore()).isEqualTo(80.0);
		assertThat(summary.scoredCount()).isEqualTo(1);
		assertThat(summary.averageManagerRating()).isEqualTo(3.0);
	}

	@Test
	void everythingIsReadForTheSelectedCyclesDatesAndId() {
		scorecard(scored(asha, 50.0));

		service.overview(1L);

		verify(kpiService).scorecard(start, end);
		verify(feedbackRepository).findByCycleIdWithTeacher(1L);
		verify(goalRepository).findByCycleIdWithTeacher(1L);
	}

	@Test
	void rowsCarryTheProfileDetailsAndAreListedAlphabetically() {
		scorecard(scored(asha, 90.0), scored(ben, 60.0), scored(chitra, null));
		List<StaffProfile> profiles = List.of(profile(asha, "EMP-001", "Senior Teacher", "Science"), profile(ben, "EMP-002", "Teacher", null));
		when(staffProfileRepository.findAll()).thenReturn(profiles);

		var overview = service.overview(1L);

		assertThat(overview.staff()).extracting(r -> r.name()).containsExactly("Asha Nair", "Ben Kurian", "Chitra Rao");
		assertThat(overview.staff().get(0).employeeCode()).isEqualTo("EMP-001");
		assertThat(overview.staff().get(0).designation()).isEqualTo("Senior Teacher");
		assertThat(overview.staff().get(0).department()).isEqualTo("Science");
		assertThat(overview.staff().get(1).department()).isNull();
		assertThat(overview.staff().get(2).employeeCode()).isNull();
	}

	@Test
	void departmentAveragesIgnoreUnscoredStaff() {
		scorecard(scored(asha, 90.0), scored(ben, 60.0), scored(chitra, null));
		List<StaffProfile> profiles = List.of(profile(asha, "EMP-001", "Senior Teacher", "Science"), profile(ben, "EMP-002", "Teacher", null));
		when(staffProfileRepository.findAll()).thenReturn(profiles);

		var departments = service.overview(1L).insights().departments();

		assertThat(departments).extracting(d -> d.department()).containsExactly(PerformanceOverviewService.NO_DEPARTMENT, "Science");
		var none = departments.get(0);
		assertThat(none.staff()).isEqualTo(2);
		assertThat(none.scored()).isEqualTo(1);
		assertThat(none.averageScore()).isEqualTo(60.0);
		assertThat(departments.get(1).averageScore()).isEqualTo(90.0);
	}

	@Test
	void unnamedOrProfilelessStaffNeverShowRawIdentifiers() {
		Person blank = person(4, null, "  ");
		scorecard(scored(blank, null));

		var row = service.overview(1L).staff().get(0);

		assertThat(row.name()).isEqualTo("Unnamed staff member");
		assertThat(row.employeeCode()).isNull();
		assertThat(row.designation()).isNull();
		assertThat(row.department()).isNull();
	}

	@Test
	void ratingDistributionAndGoalStatsCountOnlyWhatIsRecorded() {
		scorecard(scored(asha, 70.0));
		when(feedbackRepository.findByCycleIdWithTeacher(1L)).thenReturn(List.of(
				new TeacherFeedback(cycle, asha, reviewer, FeedbackSource.MANAGER, 4, null),
				new TeacherFeedback(cycle, asha, ben, FeedbackSource.PEER, 1, null),
				new TeacherFeedback(cycle, asha, asha, FeedbackSource.SELF, 5, null)));
		TeacherGoal achieved = new TeacherGoal(cycle, asha, "Reading club", null);
		achieved.markAs(GoalStatus.ACHIEVED);
		when(goalRepository.findByCycleIdWithTeacher(1L)).thenReturn(List.of(achieved, new TeacherGoal(cycle, asha, "Mentoring", null)));

		var insights = service.overview(1L).insights();

		assertThat(insights.managerRatingCounts()).containsExactly(0, 0, 0, 1, 0);
		assertThat(insights.goals().total()).isEqualTo(2);
		assertThat(insights.goals().achieved()).isEqualTo(1);
		assertThat(insights.goals().open()).isEqualTo(1);
		assertThat(service.overview(1L).staff().get(0).goalsAchieved()).isEqualTo(1);
	}

	@Test
	void presenceTrendIsMonthlyAndTreatsLeaveAsNeitherPresentNorAbsent() {
		scorecard(scored(asha, 70.0));
		List<StaffAttendance> records = List.of(
				attendance(asha, LocalDate.of(2026, 6, 2), AttendanceStatus.PRESENT),
				attendance(asha, LocalDate.of(2026, 6, 3), AttendanceStatus.ABSENT),
				attendance(asha, LocalDate.of(2026, 6, 4), AttendanceStatus.LEAVE),
				attendance(asha, LocalDate.of(2026, 7, 1), AttendanceStatus.LATE),
				attendance(ben, LocalDate.of(2026, 7, 1), AttendanceStatus.ABSENT));
		when(attendanceRepository.findByAttendanceDateBetween(start, end)).thenReturn(records);

		var trend = service.overview(1L).insights().presenceTrend();

		// Ben is not in the cycle's roster, so July is Asha's single (late, still attended) day.
		assertThat(trend).extracting(p -> p.month()).containsExactly("2026-06", "2026-07");
		assertThat(trend.get(0).presencePercent()).isEqualTo(50.0);
		assertThat(trend.get(0).sample()).isEqualTo(2);
		assertThat(trend.get(1).presencePercent()).isEqualTo(100.0);
	}

	@Test
	void presenceTargetIsOnlyReportedWhenThePresenceMetricIsSwitchedOn() {
		scorecard(scored(asha, 70.0));
		assertThat(service.overview(1L).presenceTarget()).isEqualTo(95.0);

		when(kpiService.definitions()).thenReturn(List.of(new KpiService.Definition(KpiMetric.STAFF_PRESENCE, false, 1, BigDecimal.valueOf(95), null)));
		assertThat(service.overview(1L).presenceTarget()).isNull();
	}

	@Test
	void cycleSummariesCountManagerReviewsAgainstTheActiveTeachersAndAnyoneAlreadyRated() {
		TeacherAssignment a1 = mock(TeacherAssignment.class);
		when(a1.getTeacherPerson()).thenReturn(asha);
		TeacherAssignment a2 = mock(TeacherAssignment.class);
		when(a2.getTeacherPerson()).thenReturn(ben);
		when(assignmentRepository.findByStatus(TeacherAssignmentStatus.ACTIVE)).thenReturn(List.of(a1, a2));
		when(cycleRepository.findAllByOrderByStartDateDescIdDesc()).thenReturn(List.of(cycle));
		// Chitra has since left, but was rated in this cycle; Ben only self-rated.
		when(feedbackRepository.findAllRatedTeachers()).thenReturn(List.of(
				new TeacherFeedbackRepository.RatedTeacher(1L, 1L, FeedbackSource.MANAGER),
				new TeacherFeedbackRepository.RatedTeacher(1L, 3L, FeedbackSource.MANAGER),
				new TeacherFeedbackRepository.RatedTeacher(1L, 2L, FeedbackSource.SELF)));

		var summary = service.cycleSummaries().get(0);

		assertThat(summary.staffCount()).isEqualTo(3);
		assertThat(summary.completed()).isEqualTo(2);
		assertThat(summary.pending()).isEqualTo(1);
	}

	private static StaffAttendance attendance(Person person, LocalDate date, AttendanceStatus status) {
		StaffAttendance record = mock(StaffAttendance.class);
		when(record.getPerson()).thenReturn(person);
		when(record.getAttendanceDate()).thenReturn(date);
		when(record.getAttendanceStatus()).thenReturn(status);
		return record;
	}
}
