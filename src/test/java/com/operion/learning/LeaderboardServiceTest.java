package com.operion.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.examination.Exam;
import com.operion.examination.ExamRepository;
import com.operion.examination.ExamSchedule;
import com.operion.examination.ExamScheduleRepository;
import com.operion.examination.ExamType;
import com.operion.examination.MarksEntry;
import com.operion.examination.MarksEntryRepository;
import com.operion.learning.LeaderboardService.Track;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Separate student tracks, reward granting rules and the activity-based teacher board (#231). */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, LeaderboardService.class, QuizService.class, HomeworkService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LeaderboardServiceTest extends LearningTestBase {

	@Autowired private LeaderboardService leaderboardService;
	@Autowired private QuizService quizService;
	@Autowired private ExamRepository examRepository;
	@Autowired private ExamScheduleRepository scheduleRepository;
	@Autowired private MarksEntryRepository marksEntryRepository;

	private void sittingQuiz(int raviCorrect, int sitaCorrect) {
		Instant now = Instant.now();
		Quiz quiz = quizService.createQuiz(ashaTeachesMathsInA, sectionA.getId(), maths.getId(), "Q", null, now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)));
		List<QuizService.OptionInput> options = List.of(new QuizService.OptionInput("wrong", false), new QuizService.OptionInput("right", true));
		List<QuizService.QuestionView> questions = List.of(quizService.addQuestion(ashaTeachesMathsInA, quiz.getId(), "one", 1, options),
				quizService.addQuestion(ashaTeachesMathsInA, quiz.getId(), "two", 1, options));
		quizService.publish(ashaTeachesMathsInA, quiz.getId());
		attempt(quiz, raviEnrollment, questions, raviCorrect, now);
		attempt(quiz, sitaEnrollment, questions, sitaCorrect, now);
	}

	private void attempt(Quiz quiz, com.operion.student.StudentEnrollment enrollment, List<QuizService.QuestionView> questions, int correct, Instant now) {
		Map<Long, Long> answers = new HashMap<>();
		for (int i = 0; i < questions.size(); i++) {
			boolean right = i < correct;
			answers.put(questions.get(i).question().getId(),
					questions.get(i).options().stream().filter(o -> o.isCorrect() == right).findFirst().orElseThrow().getId());
		}
		quizService.submitAttempt(guardianOf(enrollment), quiz.getId(), answers, now);
	}

	@Test
	void quizTrackRanksByAveragePercentageAndTiesShareARank() {
		sittingQuiz(2, 1);
		var board = leaderboardService.studentBoard(ashaTeachesMathsInA, sectionA.getId(), Track.QUIZ);
		assertThat(board).extracting(LeaderboardService.BoardEntry::rank).containsExactly(1, 2);
		assertThat(board.get(0).enrollment().getId()).isEqualTo(raviEnrollment.getId());
		assertThat(board.get(0).score()).isEqualTo(100.0);
	}

	@Test
	void academicTrackUsesMarksAndExcludesAbsences() {
		Exam exam = examRepository.save(new Exam(year, "Term 1", ExamType.UNIT_TEST));
		ExamSchedule schedule = scheduleRepository.save(new ExamSchedule(exam, schoolClass, maths, LocalDate.of(2025, 9, 1), 50.0, 20.0));
		marksEntryRepository.save(new MarksEntry(schedule, raviEnrollment, 40.0, false, null));
		marksEntryRepository.save(new MarksEntry(schedule, sitaEnrollment, 0.0, true, null));
		var board = leaderboardService.studentBoard(ashaTeachesMathsInA, sectionA.getId(), Track.ACADEMIC);
		assertThat(board).hasSize(1);
		assertThat(board.get(0).score()).isEqualTo(80.0);
		assertThat(leaderboardService.studentBoard(ashaTeachesMathsInA, sectionA.getId(), Track.QUIZ)).isEmpty();
	}

	@Test
	void rewardsAccumulateOnTheRewardTrackOnlyAndTeachersOfOtherSectionsCannotGrant() {
		// The controller maps the granter's name after the service transaction ends, so it must be a loaded entity, not a proxy.
		assertThat(leaderboardService.grant(ashaTeachesMathsInA, sitaEnrollment.getId(), "Helpful", 5, null, Instant.now()).getGrantedBy().getFirstName())
				.isEqualTo("Asha");
		leaderboardService.grant(admin, sitaEnrollment.getId(), "Neat work", 3, "Great notebook", Instant.now());
		leaderboardService.grant(ashaTeachesMathsInA, raviEnrollment.getId(), "Punctual", 4, null, Instant.now());
		var board = leaderboardService.studentBoard(ashaTeachesMathsInA, sectionA.getId(), Track.REWARD);
		assertThat(board.get(0).enrollment().getId()).isEqualTo(sitaEnrollment.getId());
		assertThat(board.get(0).score()).isEqualTo(8.0);
		assertThat(leaderboardService.studentBoard(ashaTeachesMathsInA, sectionA.getId(), Track.QUIZ)).isEmpty();
		assertThatThrownBy(() -> leaderboardService.grant(balaTeachesScienceInB, sitaEnrollment.getId(), "x", 1, null, Instant.now()))
				.isInstanceOf(AuthorizationDeniedException.class);
		assertThat(leaderboardService.rewardsInSection(ashaTeachesMathsInA, sectionA.getId())).hasSize(3);
	}

	@Test
	void grantValidatesTitleAndPoints() {
		assertThatThrownBy(() -> leaderboardService.grant(admin, raviEnrollment.getId(), " ", 5, null, Instant.now())).hasMessageContaining("title");
		assertThatThrownBy(() -> leaderboardService.grant(admin, raviEnrollment.getId(), "x", 0, null, Instant.now())).hasMessageContaining("between 1 and");
		assertThatThrownBy(() -> leaderboardService.grant(admin, raviEnrollment.getId(), "x", 101, null, Instant.now())).hasMessageContaining("between 1 and");
	}

	@Test
	void studentBoardIsScopedToTheCallersSections() {
		assertThatThrownBy(() -> leaderboardService.studentBoard(balaTeachesScienceInB, sectionA.getId(), Track.REWARD))
				.isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void standingShowsOwnRankPerTrackWithoutOthers() {
		leaderboardService.grant(admin, sitaEnrollment.getId(), "Helpful", 5, null, Instant.now());
		Map<Track, LeaderboardService.Standing> raviStanding = leaderboardService.standing(raviEnrollment);
		assertThat(raviStanding.get(Track.REWARD).rank()).isNull();
		Map<Track, LeaderboardService.Standing> sitaStanding = leaderboardService.standing(sitaEnrollment);
		assertThat(sitaStanding.get(Track.REWARD).rank()).isEqualTo(1);
		assertThat(sitaStanding.get(Track.REWARD).of()).isEqualTo(1);
	}

	@Test
	void teacherBoardCountsActivityAndIsAdminOnly() {
		sittingQuiz(1, 1);
		leaderboardService.grant(ashaTeachesMathsInA, raviEnrollment.getId(), "x", 1, null, Instant.now());
		var board = leaderboardService.teacherBoard(admin);
		assertThat(board).hasSize(1);
		assertThat(board.get(0).quizzesPublished()).isEqualTo(1);
		assertThat(board.get(0).points()).isEqualTo(LeaderboardService.POINTS_QUIZ);
		assertThat(board.get(0).rank()).isEqualTo(1);
		assertThatThrownBy(() -> leaderboardService.teacherBoard(ashaTeachesMathsInA)).isInstanceOf(AuthorizationDeniedException.class);
	}
}
