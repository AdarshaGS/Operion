package com.operion.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.common.InvalidRequestException;
import com.operion.identity.Person;
import com.operion.learning.LessonPlan;
import com.operion.learning.LessonPlanRepository;
import org.junit.jupiter.api.Test;

/** Scoring and provider arithmetic, with the repositories mocked - no database needed. */
class KpiServiceTest {

	private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
	private static final LocalDate TO = LocalDate.of(2026, 9, 30);
	private static final KpiPeriod PERIOD = new KpiPeriod(FROM, TO);

	private final KpiDefinitionRepository definitionRepository = mock(KpiDefinitionRepository.class);
	private final TeacherAssignmentRepository assignmentRepository = mock(TeacherAssignmentRepository.class);
	private final KpiMetricProvider provider = mock(KpiMetricProvider.class);
	private final KpiService service = new KpiService(definitionRepository, assignmentRepository, List.of(provider));

	private static Person person(long id, String first, String last) {
		Person person = mock(Person.class);
		when(person.getId()).thenReturn(id);
		when(person.getFirstName()).thenReturn(first);
		when(person.getLastName()).thenReturn(last);
		return person;
	}

	private void teachers(Person... people) {
		List<TeacherAssignment> assignments = java.util.Arrays.stream(people).map(p -> {
			TeacherAssignment a = mock(TeacherAssignment.class);
			when(a.getTeacherPerson()).thenReturn(p);
			return a;
		}).toList();
		when(assignmentRepository.findByStatus(TeacherAssignmentStatus.ACTIVE)).thenReturn(assignments);
	}

	private static KpiDefinition enabled(KpiMetric metric, int weight, double target) {
		KpiDefinition d = new KpiDefinition(metric);
		d.configure(true, weight, BigDecimal.valueOf(target), metric.defaultDays());
		return d;
	}

	@Test
	void nothingIsTrackedUntilTheOrganisationOptsIn() {
		when(definitionRepository.findAll()).thenReturn(List.of());
		teachers(person(1, "Anil", "Rao"));

		KpiService.Scorecard scorecard = service.scorecard(FROM, TO);

		assertThat(scorecard.metrics()).isEmpty();
		assertThat(scorecard.teachers()).singleElement().satisfies(row -> {
			assertThat(row.overallScore()).isNull();
			assertThat(row.cells()).isEmpty();
		});
		assertThat(service.definitions()).hasSize(KpiMetric.values().length).noneMatch(KpiService.Definition::enabled);
	}

	@Test
	void scoresAgainstTheTargetAndWeighsMetrics() {
		when(definitionRepository.findAll()).thenReturn(List.of(enabled(KpiMetric.STAFF_PUNCTUALITY, 3, 90), enabled(KpiMetric.STAFF_PRESENCE, 1, 100)));
		Person anil = person(1, "Anil", "Rao");
		teachers(anil);
		when(provider.supports()).thenReturn(Set.of(KpiMetric.STAFF_PUNCTUALITY, KpiMetric.STAFF_PRESENCE));
		// 81% against a 90% target = 90 score; 60% against 100% = 60 score
		when(provider.compute(eq(KpiMetric.STAFF_PUNCTUALITY), eq(PERIOD), any())).thenReturn(Map.of(1L, new MetricValue(81, 20)));
		when(provider.compute(eq(KpiMetric.STAFF_PRESENCE), eq(PERIOD), any())).thenReturn(Map.of(1L, new MetricValue(60, 20)));

		KpiService.TeacherRow row = service.scorecard(FROM, TO).teachers().get(0);

		// (90*3 + 60*1) / 4
		assertThat(row.overallScore()).isEqualTo(82.5);
	}

	@Test
	void beatingTheTargetCapsAtFullMarks() {
		when(definitionRepository.findAll()).thenReturn(List.of(enabled(KpiMetric.STAFF_PRESENCE, 1, 80)));
		teachers(person(1, "Anil", "Rao"));
		when(provider.supports()).thenReturn(Set.of(KpiMetric.STAFF_PRESENCE));
		when(provider.compute(eq(KpiMetric.STAFF_PRESENCE), eq(PERIOD), any())).thenReturn(Map.of(1L, new MetricValue(100, 10)));

		assertThat(service.scorecard(FROM, TO).teachers().get(0).overallScore()).isEqualTo(100.0);
	}

	@Test
	void aMetricWithNoRecordsIsLeftOutRatherThanScoredZero() {
		when(definitionRepository.findAll()).thenReturn(List.of(enabled(KpiMetric.STAFF_PUNCTUALITY, 1, 100), enabled(KpiMetric.STAFF_PRESENCE, 1, 100)));
		teachers(person(1, "Anil", "Rao"), person(2, "Bela", "Shah"));
		when(provider.supports()).thenReturn(Set.of(KpiMetric.STAFF_PUNCTUALITY, KpiMetric.STAFF_PRESENCE));
		when(provider.compute(eq(KpiMetric.STAFF_PUNCTUALITY), eq(PERIOD), any())).thenReturn(Map.of(1L, new MetricValue(50, 10)));
		when(provider.compute(eq(KpiMetric.STAFF_PRESENCE), eq(PERIOD), any())).thenReturn(Map.of());

		List<KpiService.TeacherRow> rows = service.scorecard(FROM, TO).teachers();

		assertThat(rows.get(0).person().getLastName()).isEqualTo("Rao");
		assertThat(rows.get(0).overallScore()).isEqualTo(50.0);
		assertThat(rows.get(1).overallScore()).isNull();
		assertThat(rows.get(1).cells()).allMatch(c -> c.value() == null && c.score() == null);
	}

	@Test
	void rejectsAnInvertedPeriod() {
		assertThatThrownBy(() -> service.scorecard(TO, FROM)).isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void configureValidatesWeightAndTarget() {
		when(definitionRepository.findByMetric(any())).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.configure(KpiMetric.STAFF_PRESENCE, true, 0, BigDecimal.valueOf(90), null))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.configure(KpiMetric.STAFF_PRESENCE, true, 1, BigDecimal.ZERO, null))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.configure(KpiMetric.STAFF_PRESENCE, true, 1, BigDecimal.valueOf(101), null))
				.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void lessonPlanMetricsCountOnTimeSubmissionsAndReviewedOutcomes() {
		LessonPlanRepository planRepository = mock(LessonPlanRepository.class);
		Person author = person(7, "Anil", "Rao");
		Person reviewer = person(9, "Head", "Teacher");
		LocalDate planDate = LocalDate.of(2026, 9, 10);
		Instant dayBefore = planDate.minusDays(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant();
		Instant dayAfter = planDate.plusDays(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant();

		LessonPlan onTimeApproved = new LessonPlan(null, null, planDate, "A", null, null, null, author);
		onTimeApproved.submit(dayBefore);
		onTimeApproved.approve(reviewer, null, dayBefore);
		LessonPlan lateRejected = new LessonPlan(null, null, planDate, "B", null, null, null, author);
		lateRejected.submit(dayAfter);
		lateRejected.reject(reviewer, "redo", dayAfter);
		LessonPlan draft = new LessonPlan(null, null, planDate, "C", null, null, null, author);
		when(planRepository.findByPlanDateBetween(FROM, TO)).thenReturn(List.of(onTimeApproved, lateRejected, draft));
		LessonPlanKpiProvider planProvider = new LessonPlanKpiProvider(planRepository);

		MetricValue onTime = planProvider.compute(KpiMetric.LESSON_PLAN_ON_TIME, PERIOD, null).get(7L);
		MetricValue approval = planProvider.compute(KpiMetric.LESSON_PLAN_APPROVAL, PERIOD, null).get(7L);

		// the unsubmitted draft is in neither denominator
		assertThat(onTime.value()).isEqualTo(50.0);
		assertThat(onTime.sample()).isEqualTo(2);
		assertThat(approval.value()).isEqualTo(50.0);
		assertThat(approval.sample()).isEqualTo(2);
	}

	@Test
	void countMetricsAllowLargerTargetsAndPercentMetricsDoNot() {
		when(definitionRepository.findByMetric(any())).thenReturn(Optional.empty());
		when(definitionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		assertThat(service.configure(KpiMetric.HOMEWORK_ISSUED, true, 1, BigDecimal.valueOf(40), null).target()).isEqualByComparingTo("40");
		assertThatThrownBy(() -> service.configure(KpiMetric.HOMEWORK_ISSUED, true, 1, BigDecimal.valueOf(1000), null))
				.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void aMetricThatUsesDaysNeedsThem() {
		when(definitionRepository.findByMetric(any())).thenReturn(Optional.empty());
		when(definitionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		assertThatThrownBy(() -> service.configure(KpiMetric.MARKS_ENTRY_ON_TIME, true, 1, BigDecimal.valueOf(90), null))
				.isInstanceOf(InvalidRequestException.class);
		assertThat(service.configure(KpiMetric.MARKS_ENTRY_ON_TIME, true, 1, BigDecimal.valueOf(90), 5).paramDays()).isEqualTo(5);
		// a metric that does not use days drops any it is sent
		assertThat(service.configure(KpiMetric.STAFF_PRESENCE, true, 1, BigDecimal.valueOf(90), 5).paramDays()).isNull();
	}

	@Test
	void outcomeMetricsAreScoredAgainstTheTeachersOwnPreviousPeriod() {
		when(definitionRepository.findAll()).thenReturn(List.of(enabled(KpiMetric.OUTCOME_PASS_RATE, 1, 100)));
		teachers(person(1, "Anil", "Rao"), person(2, "Bela", "Shah"), person(3, "Chitra", "Das"));
		when(provider.supports()).thenReturn(Set.of(KpiMetric.OUTCOME_PASS_RATE));
		when(provider.compute(eq(KpiMetric.OUTCOME_PASS_RATE), eq(PERIOD), any()))
				.thenReturn(Map.of(1L, new MetricValue(60, 30), 2L, new MetricValue(90, 30), 3L, new MetricValue(70, 30)));
		when(provider.compute(eq(KpiMetric.OUTCOME_PASS_RATE), eq(PERIOD.previous()), any()))
				.thenReturn(Map.of(1L, new MetricValue(80, 30), 2L, new MetricValue(80, 30)));

		List<KpiService.TeacherRow> rows = service.scorecard(FROM, TO).teachers();

		// dropped from 80 to 60: 75; improved from 80 to 90: capped at 100; no earlier period: shown but unscored
		// rows are alphabetical by last name: Das, Rao, Shah
		assertThat(rows.get(0).overallScore()).isNull();
		assertThat(rows.get(0).cells().get(0).value()).isEqualTo(70.0);
		assertThat(rows.get(1).overallScore()).isEqualTo(75.0);
		assertThat(rows.get(1).cells().get(0).previous()).isEqualTo(80.0);
		assertThat(rows.get(2).overallScore()).isEqualTo(100.0);
	}

	@Test
	void theWholePreviousPeriodIsTheSameLengthEndingTheDayBefore() {
		KpiPeriod previous = PERIOD.previous();

		assertThat(PERIOD.days()).isEqualTo(30);
		assertThat(previous.days()).isEqualTo(30);
		assertThat(previous.to()).isEqualTo(FROM.minusDays(1));
	}

	@Test
	void scorecardForNarrowsToOneTeacher() {
		when(definitionRepository.findAll()).thenReturn(List.of());
		teachers(person(1, "Anil", "Rao"), person(2, "Bela", "Shah"));

		assertThat(service.scorecardFor(2L, FROM, TO).teachers()).singleElement().satisfies(row -> assertThat(row.person().getId()).isEqualTo(2L));
		assertThat(service.scorecardFor(99L, FROM, TO).teachers()).isEmpty();
	}
}
