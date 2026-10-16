package com.operion.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.common.InvalidRequestException;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Quiz authoring rules, the question bank, scheduling window and auto-scoring (#227). */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, QuizService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QuizServiceTest extends LearningTestBase {

	private static final List<QuizService.OptionInput> TWO_PLUS_TWO = List.of(new QuizService.OptionInput("3", false),
			new QuizService.OptionInput("4", true), new QuizService.OptionInput("5", false));

	@Autowired private QuizService quizService;
	@Autowired private QuizOptionRepository optionRepository;
	@Autowired private QuizQuestionRepository questionRepository;
	@Autowired private QuizAttemptRepository attemptRepository;

	private Instant now;
	private Quiz quiz;

	@BeforeEach
	void setUpQuiz() {
		now = Instant.now();
		quiz = quizService.createQuiz(ashaTeachesMathsInA, sectionA.getId(), maths.getId(), "Unit 1", null,
				now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)));
	}

	private long correctOptionId(Long questionId) {
		return optionRepository.findByQuestionIdOrderByOrdinalAsc(questionId).stream().filter(QuizOption::isCorrect).findFirst()
				.orElseThrow().getId();
	}

	private long wrongOptionId(Long questionId) {
		return optionRepository.findByQuestionIdOrderByOrdinalAsc(questionId).stream().filter(o -> !o.isCorrect()).findFirst()
				.orElseThrow().getId();
	}

	@Test
	void questionsMustHaveExactlyOneCorrectOptionAndPublishNeedsQuestions() {
		assertThatThrownBy(() -> quizService.addQuestion(ashaTeachesMathsInA, quiz.getId(), "Q", 1,
				List.of(new QuizService.OptionInput("a", true), new QuizService.OptionInput("b", true))))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("exactly one");
		assertThatThrownBy(() -> quizService.addQuestion(ashaTeachesMathsInA, quiz.getId(), "Q", 1,
				List.of(new QuizService.OptionInput("only", true)))).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> quizService.publish(ashaTeachesMathsInA, quiz.getId())).isInstanceOf(IllegalStateException.class);

		quizService.addQuestion(ashaTeachesMathsInA, quiz.getId(), "2+2?", 2, TWO_PLUS_TWO);
		quizService.publish(ashaTeachesMathsInA, quiz.getId());
		assertThatThrownBy(() -> quizService.addQuestion(ashaTeachesMathsInA, quiz.getId(), "Late", 1, TWO_PLUS_TWO))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void guardianAttemptIsAutoScoredOnceAndAppearsInHistory() {
		var q1 = quizService.addQuestion(ashaTeachesMathsInA, quiz.getId(), "2+2?", 2, TWO_PLUS_TWO);
		var q2 = quizService.addQuestion(ashaTeachesMathsInA, quiz.getId(), "3+3?", 3, List.of(
				new QuizService.OptionInput("6", true), new QuizService.OptionInput("7", false)));
		quizService.publish(ashaTeachesMathsInA, quiz.getId());
		var guardian = guardianOf(raviEnrollment);

		QuizAttempt attempt = quizService.submitAttempt(guardian, quiz.getId(), Map.of(
				q1.question().getId(), correctOptionId(q1.question().getId()),
				q2.question().getId(), wrongOptionId(q2.question().getId())), now);

		assertThat(attempt.getScore()).isEqualTo(2);
		assertThat(attempt.getMaxScore()).isEqualTo(5);
		assertThat(quizService.history(ravi.getId())).hasSize(1);
		assertThatThrownBy(() -> quizService.submitAttempt(guardian, quiz.getId(), Map.of(), now))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("already been attempted");
		// Skipped questions score zero.
		assertThat(quizService.submitAttempt(guardianOf(sitaEnrollment), quiz.getId(), Map.of(), now).getScore()).isZero();
	}

	@Test
	void attemptsOnlyWithinTheWindowAndAnswerKeyHiddenUntilClose() {
		var q1 = quizService.addQuestion(ashaTeachesMathsInA, quiz.getId(), "2+2?", 1, TWO_PLUS_TWO);
		quizService.publish(ashaTeachesMathsInA, quiz.getId());
		var guardian = guardianOf(raviEnrollment);

		assertThatThrownBy(() -> quizService.submitAttempt(guardian, quiz.getId(), Map.of(), now.minus(Duration.ofHours(2))))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("not open");
		assertThatThrownBy(() -> quizService.questionsToAttempt(raviEnrollment, quiz.getId(), now.plus(Duration.ofHours(2))))
				.isInstanceOf(IllegalStateException.class);

		quizService.submitAttempt(guardian, quiz.getId(), Map.of(q1.question().getId(), correctOptionId(q1.question().getId())), now);
		assertThat(quizService.attemptDetail(raviEnrollment, quiz.getId(), now).correctAnswersShown()).isFalse();
		assertThat(quizService.attemptDetail(raviEnrollment, quiz.getId(), now.plus(Duration.ofHours(2))).correctAnswersShown()).isTrue();
		assertThat(quizService.forChild(raviEnrollment, now).get(0).attempt()).isNotNull();
	}

	@Test
	void draftQuizzesAreInvisibleToGuardiansAndForeignOptionsAreRejected() {
		assertThat(quizService.forChild(raviEnrollment, now)).isEmpty();
		assertThatThrownBy(() -> quizService.questionsToAttempt(raviEnrollment, quiz.getId(), now))
				.isInstanceOf(IllegalArgumentException.class);

		var q1 = quizService.addQuestion(ashaTeachesMathsInA, quiz.getId(), "2+2?", 1, TWO_PLUS_TWO);
		var other = quizService.addBankQuestion(ashaTeachesMathsInA, maths.getId(), "Other", 1, TWO_PLUS_TWO);
		quizService.publish(ashaTeachesMathsInA, quiz.getId());
		assertThatThrownBy(() -> quizService.submitAttempt(guardianOf(raviEnrollment), quiz.getId(),
				Map.of(q1.question().getId(), other.options().get(0).getId()), now)).isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void bankQuestionsAreCopiedIntoQuizzesAndScopedBySubject() {
		var bank = quizService.addBankQuestion(ashaTeachesMathsInA, maths.getId(), "2+2?", 2, TWO_PLUS_TWO);
		assertThatThrownBy(() -> quizService.addBankQuestion(ashaTeachesMathsInA, science.getId(), "Q", 1, TWO_PLUS_TWO))
				.isInstanceOf(AuthorizationDeniedException.class);
		assertThat(quizService.bank(balaTeachesScienceInB, null)).isEmpty();

		var copy = quizService.addFromBank(ashaTeachesMathsInA, quiz.getId(), bank.question().getId());
		assertThat(copy.question().getId()).isNotEqualTo(bank.question().getId());
		quizService.deleteBankQuestion(ashaTeachesMathsInA, bank.question().getId());

		// The quiz keeps its copy after the bank item is gone.
		assertThat(quizService.questions(ashaTeachesMathsInA, quiz.getId())).hasSize(1);
		assertThat(questionRepository.findByQuizIsNullOrderByIdDesc()).isEmpty();
		var scienceQuiz = quizService.createQuiz(balaTeachesScienceInB, sectionB.getId(), science.getId(), "S", null, now, now.plusSeconds(60));
		var mathsBank = quizService.addBankQuestion(admin, maths.getId(), "x", 1, TWO_PLUS_TWO);
		assertThatThrownBy(() -> quizService.addFromBank(balaTeachesScienceInB, scienceQuiz.getId(), mathsBank.question().getId()))
				.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void resultsListEveryEnrolledStudentAndTeachersOnlySeeTheirSections() {
		quizService.addQuestion(ashaTeachesMathsInA, quiz.getId(), "2+2?", 1, TWO_PLUS_TWO);
		quizService.publish(ashaTeachesMathsInA, quiz.getId());
		quizService.submitAttempt(guardianOf(raviEnrollment), quiz.getId(), Map.of(), now);

		assertThat(quizService.results(ashaTeachesMathsInA, quiz.getId())).extracting(r -> r.attempt() != null).containsExactly(true, false);
		assertThatThrownBy(() -> quizService.results(balaTeachesScienceInB, quiz.getId())).isInstanceOf(AuthorizationDeniedException.class);
		assertThat(quizService.list(balaTeachesScienceInB, null)).isEmpty();
		assertThat(quizService.list(ashaTeachesMathsInA, null)).hasSize(1);
		assertThat(attemptRepository.findAll()).hasSize(1);
	}
}
