package com.operion.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.audit.AuditLogService;
import com.operion.common.InvalidRequestException;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Who may rate whom, and what each side gets to see, with the repositories mocked. */
class AppraisalServiceTest {

	private final AppraisalCycleRepository cycleRepository = mock(AppraisalCycleRepository.class);
	private final TeacherFeedbackRepository feedbackRepository = mock(TeacherFeedbackRepository.class);
	private final TeacherGoalRepository goalRepository = mock(TeacherGoalRepository.class);
	private final TeacherAssignmentRepository assignmentRepository = mock(TeacherAssignmentRepository.class);
	private final PersonRepository personRepository = mock(PersonRepository.class);
	private final KpiService kpiService = mock(KpiService.class);
	private final AuditLogService auditLogService = mock(AuditLogService.class);
	private final AppraisalService service = new AppraisalService(cycleRepository, feedbackRepository, goalRepository,
			assignmentRepository, personRepository, kpiService, auditLogService);

	private final AppraisalCycle cycle = new AppraisalCycle("Term 1", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 9, 30));
	private final Person teacher = person(7, "Anil", "Rao");
	private final Person manager = person(8, "Head", "Teacher");
	private final Person colleague = person(9, "Bela", "Shah");

	private static Person person(long id, String first, String last) {
		Person person = mock(Person.class);
		when(person.getId()).thenReturn(id);
		when(person.getFirstName()).thenReturn(first);
		when(person.getLastName()).thenReturn(last);
		return person;
	}

	@BeforeEach
	void wire() {
		ReflectionTestUtils.setField(cycle, "id", 1L);
		when(cycleRepository.findById(1L)).thenReturn(Optional.of(cycle));
		when(cycleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(feedbackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(goalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		TeacherAssignment active = mock(TeacherAssignment.class);
		when(active.getStatus()).thenReturn(TeacherAssignmentStatus.ACTIVE);
		when(assignmentRepository.findByTeacherPersonId(7L)).thenReturn(List.of(active));
		when(personRepository.findById(7L)).thenReturn(Optional.of(teacher));
		when(personRepository.findById(8L)).thenReturn(Optional.of(manager));
		when(personRepository.findById(9L)).thenReturn(Optional.of(colleague));
		when(feedbackRepository.findByCycleIdAndTeacherIdAndReviewerId(any(), any(), any())).thenReturn(Optional.empty());
		when(kpiService.scorecardFor(any(), any(), any())).thenReturn(new KpiService.Scorecard(cycle.getStartDate(), cycle.getEndDate(), List.of(), List.of()));
	}

	@Test
	void ratingYourselfIsAlwaysASelfAppraisal() {
		var view = service.giveFeedback(1L, 7L, 7L, FeedbackSource.MANAGER, 4, "  steady term ");

		assertThat(view.source()).isEqualTo(FeedbackSource.SELF);
		assertThat(view.comment()).isEqualTo("steady term");
	}

	@Test
	void ratingSomeoneElseNeedsAManagerOrPeerSource() {
		assertThat(service.giveFeedback(1L, 7L, 8L, FeedbackSource.MANAGER, 5, null).source()).isEqualTo(FeedbackSource.MANAGER);
		assertThatThrownBy(() -> service.giveFeedback(1L, 7L, 8L, FeedbackSource.SELF, 5, null)).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.giveFeedback(1L, 7L, 8L, null, 5, null)).isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void ratingsMustBeOneToFiveAndTheTeacherMustBeActive() {
		assertThatThrownBy(() -> service.giveFeedback(1L, 7L, 8L, FeedbackSource.PEER, 6, null)).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.giveFeedback(1L, 7L, 8L, FeedbackSource.PEER, 0, null)).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.giveFeedback(1L, 9L, 8L, FeedbackSource.PEER, 3, null)).isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void aClosedCycleTakesNoMoreRatingsOrGoals() {
		cycle.close();

		assertThatThrownBy(() -> service.giveFeedback(1L, 7L, 8L, FeedbackSource.PEER, 3, null)).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.addGoal(1L, 7L, "Improve", null)).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.closeCycle(1L)).isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void ratingAgainReplacesTheEarlierRatingInsteadOfAddingOne() {
		TeacherFeedback earlier = new TeacherFeedback(cycle, teacher, manager, FeedbackSource.MANAGER, 2, "weak");
		when(feedbackRepository.findByCycleIdAndTeacherIdAndReviewerId(1L, 7L, 8L)).thenReturn(Optional.of(earlier));

		var view = service.giveFeedback(1L, 7L, 8L, FeedbackSource.MANAGER, 4, "better");

		assertThat(view.rating()).isEqualTo(4);
		assertThat(earlier.getRating()).isEqualTo(4);
		assertThat(earlier.getComment()).isEqualTo("better");
	}

	@Test
	void aTeacherSeesOnlyTheirSelfAppraisalWhileTheCycleIsOpen() {
		when(feedbackRepository.findByCycleIdAndTeacherId(1L, 7L)).thenReturn(List.of(
				new TeacherFeedback(cycle, teacher, teacher, FeedbackSource.SELF, 4, "mine"),
				new TeacherFeedback(cycle, teacher, manager, FeedbackSource.MANAGER, 3, "private"),
				new TeacherFeedback(cycle, teacher, colleague, FeedbackSource.PEER, 5, "private")));

		assertThat(service.selfAppraisal(1L, 7L).feedback()).singleElement().satisfies(f -> assertThat(f.source()).isEqualTo(FeedbackSource.SELF));
		assertThat(service.adminAppraisal(1L, 7L).feedback()).hasSize(3);
	}

	@Test
	void onceClosedATeacherSeesManagerRatingsNamedAndPeerRatingsAnonymous() {
		when(feedbackRepository.findByCycleIdAndTeacherId(1L, 7L)).thenReturn(List.of(
				new TeacherFeedback(cycle, teacher, manager, FeedbackSource.MANAGER, 3, "work on pacing"),
				new TeacherFeedback(cycle, teacher, colleague, FeedbackSource.PEER, 5, "great mentor")));
		cycle.close();

		var feedback = service.selfAppraisal(1L, 7L).feedback();

		assertThat(feedback).hasSize(2);
		assertThat(feedback).filteredOn(f -> f.source() == FeedbackSource.MANAGER).singleElement()
				.satisfies(f -> assertThat(f.reviewerName()).isEqualTo("Head Teacher"));
		assertThat(feedback).filteredOn(f -> f.source() == FeedbackSource.PEER).singleElement()
				.satisfies(f -> assertThat(f.reviewerName()).isNull());
	}

	@Test
	void cyclesNeedAnUnusedNameAndSaneDates() {
		when(cycleRepository.existsByNameIgnoreCase("Term 1")).thenReturn(true);

		assertThatThrownBy(() -> service.createCycle("Term 1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.createCycle("Term 2", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)))
				.isInstanceOf(InvalidRequestException.class);
		assertThat(service.createCycle("Term 2", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 20)).status()).isEqualTo(AppraisalCycleStatus.OPEN);
	}

	@Test
	void goalsCanBeMarkedAchievedByAReviewer() {
		TeacherGoal goal = new TeacherGoal(cycle, teacher, "Run a reading club", null);
		when(goalRepository.findById(5L)).thenReturn(Optional.of(goal));

		var view = service.updateGoal(5L, "Run a reading club", "weekly", GoalStatus.ACHIEVED);

		assertThat(view.status()).isEqualTo(GoalStatus.ACHIEVED);
		assertThat(view.description()).isEqualTo("weekly");
		assertThatThrownBy(() -> service.updateGoal(5L, " ", null, null)).isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void aRevisedRatingLeavesTheEarlierOneInTheAuditTrail() {
		TeacherFeedback earlier = new TeacherFeedback(cycle, teacher, manager, FeedbackSource.MANAGER, 2, "weak");
		when(feedbackRepository.findByCycleIdAndTeacherIdAndReviewerId(1L, 7L, 8L)).thenReturn(Optional.of(earlier));

		service.giveFeedback(1L, 7L, 8L, FeedbackSource.MANAGER, 4, "better");

		var before = org.mockito.ArgumentCaptor.forClass(Object.class);
		var after = org.mockito.ArgumentCaptor.forClass(Object.class);
		verify(auditLogService).record(eq("TeacherFeedback"), any(), eq("REVISE"), before.capture(), after.capture());
		assertThat(before.getValue().toString()).contains("rating=2").contains("weak");
		assertThat(after.getValue().toString()).contains("rating=4").contains("better");
	}

	@Test
	void aFirstRatingIsAuditedAsCreatedAndClosingACycleIsAudited() {
		service.giveFeedback(1L, 7L, 8L, FeedbackSource.MANAGER, 4, null);
		verify(auditLogService).record(eq("TeacherFeedback"), any(), eq("CREATE"), isNull(), any());

		service.closeCycle(1L);
		verify(auditLogService).record(eq("AppraisalCycle"), eq(1L), eq("CLOSE"), any(), any());
	}

	@Test
	void theViewerOwnRatingIsFlaggedSoTheFormCanOpenPrefilledAndPeerIdentityStaysHidden() {
		when(feedbackRepository.findByCycleIdAndTeacherId(1L, 7L)).thenReturn(List.of(
				new TeacherFeedback(cycle, teacher, manager, FeedbackSource.MANAGER, 3, "mine"),
				new TeacherFeedback(cycle, teacher, colleague, FeedbackSource.PEER, 5, "theirs")));

		var asManager = service.adminAppraisal(1L, 7L, 8L).feedback();

		assertThat(asManager).filteredOn(f -> f.source() == FeedbackSource.MANAGER).singleElement().satisfies(f -> assertThat(f.mine()).isTrue());
		assertThat(asManager).filteredOn(f -> f.source() == FeedbackSource.PEER).singleElement().satisfies(f -> assertThat(f.mine()).isFalse());
		assertThat(service.adminAppraisal(1L, 7L).feedback()).noneMatch(AppraisalService.FeedbackView::mine);
		cycle.close();
		assertThat(service.selfAppraisal(1L, 7L).feedback()).noneMatch(AppraisalService.FeedbackView::mine);
	}
}
