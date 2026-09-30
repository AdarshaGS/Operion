package com.operion.learning;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quizzes (#227): authoring with a reusable question bank, scheduling, a single auto-scored
 * attempt per student (submitted by a linked guardian), and per-student history.
 */
@Service
@Transactional
public class QuizService {

	public record OptionInput(String text, boolean correct) {
	}

	public record QuizView(Quiz quiz, int questionCount, int totalMarks, int attempts, int enrolled) {
	}

	public record QuestionView(QuizQuestion question, List<QuizOption> options) {
	}

	public record ResultRow(StudentEnrollment enrollment, QuizAttempt attempt) {
	}

	public record ChildQuizView(Quiz quiz, Quiz.Availability availability, QuizAttempt attempt, int questionCount, int totalMarks) {
	}

	public record AnswerDetail(QuizQuestion question, List<QuizOption> options, Long selectedOptionId, boolean correct, int marksAwarded) {
	}

	/** correctAnswersShown is false while the quiz is still open, so an answer key can't leak to a sibling still to attempt it. */
	public record AttemptDetail(QuizAttempt attempt, List<AnswerDetail> answers, boolean correctAnswersShown) {
	}

	private final QuizRepository quizRepository;
	private final QuizQuestionRepository questionRepository;
	private final QuizOptionRepository optionRepository;
	private final QuizAttemptRepository attemptRepository;
	private final QuizAttemptAnswerRepository answerRepository;
	private final SectionRepository sectionRepository;
	private final SubjectRepository subjectRepository;
	private final PersonRepository personRepository;
	private final StudentRepository studentRepository;
	private final StudentEnrollmentRepository enrollmentRepository;

	public QuizService(QuizRepository quizRepository, QuizQuestionRepository questionRepository,
			QuizOptionRepository optionRepository, QuizAttemptRepository attemptRepository,
			QuizAttemptAnswerRepository answerRepository, SectionRepository sectionRepository,
			SubjectRepository subjectRepository, PersonRepository personRepository, StudentRepository studentRepository,
			StudentEnrollmentRepository enrollmentRepository) {
		this.quizRepository = quizRepository;
		this.questionRepository = questionRepository;
		this.optionRepository = optionRepository;
		this.attemptRepository = attemptRepository;
		this.answerRepository = answerRepository;
		this.sectionRepository = sectionRepository;
		this.subjectRepository = subjectRepository;
		this.personRepository = personRepository;
		this.studentRepository = studentRepository;
		this.enrollmentRepository = enrollmentRepository;
	}

	// --- authoring ---

	public Quiz createQuiz(LearningActor actor, Long sectionId, Long subjectId, String title, String instructions,
			Instant opensAt, Instant closesAt) {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("Title is required");
		}
		if (opensAt == null || closesAt == null || !closesAt.isAfter(opensAt)) {
			throw new IllegalArgumentException("The quiz must close after it opens");
		}
		actor.requireWrite(sectionId, subjectId);
		Section section = sectionRepository.findById(sectionId)
				.orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
		Subject subject = subjectRepository.findById(subjectId)
				.orElseThrow(() -> new IllegalArgumentException("Subject not found: " + subjectId));
		return quizRepository.save(new Quiz(section, subject, title.trim(), blankToNull(instructions), opensAt, closesAt,
				personRepository.findById(actor.personId()).orElseThrow()));
	}

	public QuestionView addQuestion(LearningActor actor, Long quizId, String text, int marks, List<OptionInput> options) {
		Quiz quiz = requireDraft(actor, quizId);
		QuizQuestion question = saveQuestion(quiz, quiz.getSubject(), nextOrdinal(quizId), text, marks, options, actor);
		return new QuestionView(question, optionRepository.findByQuestionIdOrderByOrdinalAsc(question.getId()));
	}

	/** Copies a bank question into the quiz - later edits or deletion of the bank item never touch the quiz. */
	public QuestionView addFromBank(LearningActor actor, Long quizId, Long bankQuestionId) {
		Quiz quiz = requireDraft(actor, quizId);
		QuizQuestion bank = questionRepository.findById(bankQuestionId).filter(q -> q.getQuiz() == null)
				.orElseThrow(() -> new IllegalArgumentException("Question bank item not found: " + bankQuestionId));
		if (!bank.getSubject().getId().equals(quiz.getSubject().getId())) {
			throw new IllegalArgumentException("That bank question belongs to a different subject");
		}
		List<OptionInput> options = optionRepository.findByQuestionIdOrderByOrdinalAsc(bank.getId()).stream()
				.map(o -> new OptionInput(o.getText(), o.isCorrect())).toList();
		QuizQuestion copy = saveQuestion(quiz, quiz.getSubject(), nextOrdinal(quizId), bank.getText(), bank.getMarks(), options, actor);
		return new QuestionView(copy, optionRepository.findByQuestionIdOrderByOrdinalAsc(copy.getId()));
	}

	public void removeQuestion(LearningActor actor, Long quizId, Long questionId) {
		requireDraft(actor, quizId);
		QuizQuestion question = questionRepository.findById(questionId)
				.filter(q -> q.getQuiz() != null && q.getQuiz().getId().equals(quizId))
				.orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
		optionRepository.deleteAll(optionRepository.findByQuestionIdOrderByOrdinalAsc(questionId));
		questionRepository.delete(question);
	}

	public Quiz publish(LearningActor actor, Long quizId) {
		Quiz quiz = requireDraft(actor, quizId);
		if (!quiz.getClosesAt().isAfter(Instant.now())) {
			throw new IllegalStateException("The closing time has already passed - reschedule the quiz first");
		}
		if (questionRepository.findByQuizIdOrderByOrdinalAsc(quizId).isEmpty()) {
			throw new IllegalStateException("Add at least one question before publishing");
		}
		quiz.publish();
		return quiz;
	}

	// --- question bank ---

	public QuestionView addBankQuestion(LearningActor actor, Long subjectId, String text, int marks, List<OptionInput> options) {
		if (!actor.canAuthorSubject(subjectId)) {
			throw new AuthorizationDeniedException("You do not teach this subject");
		}
		Subject subject = subjectRepository.findById(subjectId)
				.orElseThrow(() -> new IllegalArgumentException("Subject not found: " + subjectId));
		QuizQuestion question = saveQuestion(null, subject, 0, text, marks, options, actor);
		return new QuestionView(question, optionRepository.findByQuestionIdOrderByOrdinalAsc(question.getId()));
	}

	public void deleteBankQuestion(LearningActor actor, Long questionId) {
		QuizQuestion question = questionRepository.findById(questionId).filter(q -> q.getQuiz() == null)
				.orElseThrow(() -> new IllegalArgumentException("Question bank item not found: " + questionId));
		if (!actor.canAuthorSubject(question.getSubject().getId())) {
			throw new AuthorizationDeniedException("You do not teach this subject");
		}
		optionRepository.deleteAll(optionRepository.findByQuestionIdOrderByOrdinalAsc(questionId));
		questionRepository.delete(question);
	}

	@Transactional(readOnly = true)
	public List<QuestionView> bank(LearningActor actor, Long subjectId) {
		List<QuizQuestion> questions = subjectId == null ? questionRepository.findByQuizIsNullOrderByIdDesc()
				: questionRepository.findByQuizIsNullAndSubjectIdOrderByIdDesc(subjectId);
		return withOptions(questions.stream()
				.filter(q -> actor.seesAllSections() || actor.canAuthorSubject(q.getSubject().getId())).toList());
	}

	// --- teacher/admin reads ---

	@Transactional(readOnly = true)
	public List<QuizView> list(LearningActor actor, Long sectionId) {
		List<Quiz> quizzes;
		if (sectionId != null) {
			actor.requireReadSection(sectionId);
			quizzes = quizRepository.findAllByOrderByOpensAtDescIdDesc().stream()
					.filter(q -> q.getSection().getId().equals(sectionId)).toList();
		} else if (actor.seesAllSections()) {
			quizzes = quizRepository.findAllByOrderByOpensAtDescIdDesc();
		} else {
			quizzes = quizRepository.findBySectionIdInOrderByOpensAtDescIdDesc(actor.teachingSectionIds());
		}
		if (quizzes.isEmpty()) {
			return List.of();
		}
		List<Long> ids = quizzes.stream().map(Quiz::getId).toList();
		Map<Long, List<QuizQuestion>> questions = questionRepository.findByQuizIdIn(ids).stream()
				.collect(Collectors.groupingBy(q -> q.getQuiz().getId()));
		Map<Long, Long> attempts = attemptRepository.findByQuizIdIn(ids).stream()
				.collect(Collectors.groupingBy(a -> a.getQuiz().getId(), Collectors.counting()));
		Map<Long, Integer> enrolled = new HashMap<>();
		return quizzes.stream().map(q -> {
			List<QuizQuestion> qs = questions.getOrDefault(q.getId(), List.of());
			int size = enrolled.computeIfAbsent(q.getSection().getId(),
					id -> enrollmentRepository.findBySectionIdAndCurrentTrue(id).size());
			return new QuizView(q, qs.size(), qs.stream().mapToInt(QuizQuestion::getMarks).sum(),
					attempts.getOrDefault(q.getId(), 0L).intValue(), size);
		}).toList();
	}

	/** Full question list including which option is correct - only for callers who can see the section. */
	@Transactional(readOnly = true)
	public List<QuestionView> questions(LearningActor actor, Long quizId) {
		Quiz quiz = find(quizId);
		actor.requireReadSection(quiz.getSection().getId());
		return withOptions(questionRepository.findByQuizIdOrderByOrdinalAsc(quizId));
	}

	@Transactional(readOnly = true)
	public List<ResultRow> results(LearningActor actor, Long quizId) {
		Quiz quiz = find(quizId);
		actor.requireReadSection(quiz.getSection().getId());
		Map<Long, QuizAttempt> byStudent = attemptRepository.findByQuizId(quizId).stream()
				.collect(Collectors.toMap(a -> a.getStudent().getId(), Function.identity()));
		return enrollmentRepository.findBySectionIdAndCurrentTrue(quiz.getSection().getId()).stream()
				.sorted(Comparator.comparing(e -> e.getRollNumber() == null ? Integer.MAX_VALUE : e.getRollNumber()))
				.map(e -> new ResultRow(e, byStudent.get(e.getStudent().getId()))).toList();
	}

	// --- guardian side ---

	@Transactional(readOnly = true)
	public List<ChildQuizView> forChild(StudentEnrollment enrollment, Instant now) {
		List<Quiz> quizzes = quizRepository.findBySectionIdAndStatusOrderByOpensAtDescIdDesc(enrollment.getSection().getId(),
				QuizStatus.PUBLISHED);
		if (quizzes.isEmpty()) {
			return List.of();
		}
		List<Long> ids = quizzes.stream().map(Quiz::getId).toList();
		Map<Long, List<QuizQuestion>> questions = questionRepository.findByQuizIdIn(ids).stream()
				.collect(Collectors.groupingBy(q -> q.getQuiz().getId()));
		Map<Long, QuizAttempt> attempts = attemptRepository.findByStudentIdOrderBySubmittedAtDesc(enrollment.getStudent().getId()).stream()
				.collect(Collectors.toMap(a -> a.getQuiz().getId(), Function.identity()));
		return quizzes.stream().map(q -> {
			List<QuizQuestion> qs = questions.getOrDefault(q.getId(), List.of());
			return new ChildQuizView(q, q.availability(now), attempts.get(q.getId()), qs.size(),
					qs.stream().mapToInt(QuizQuestion::getMarks).sum());
		}).toList();
	}

	/** The questions to answer - options carry no correctness (the DTO drops it), and only while the quiz is open and unattempted. */
	@Transactional(readOnly = true)
	public List<QuestionView> questionsToAttempt(StudentEnrollment enrollment, Long quizId, Instant now) {
		Quiz quiz = requireOwnPublished(enrollment, quizId);
		if (quiz.availability(now) != Quiz.Availability.OPEN) {
			throw new IllegalStateException("This quiz is not open right now");
		}
		if (attemptRepository.findByQuizIdAndStudentId(quizId, enrollment.getStudent().getId()).isPresent()) {
			throw new IllegalStateException("This quiz has already been attempted");
		}
		return withOptions(questionRepository.findByQuizIdOrderByOrdinalAsc(quizId));
	}

	/** answers maps questionId to the chosen optionId; an absent question scores zero. */
	public QuizAttempt submitAttempt(ChildAccessService.OwnChild child, Long quizId, Map<Long, Long> answers, Instant now) {
		StudentEnrollment enrollment = child.enrollment();
		Quiz quiz = requireOwnPublished(enrollment, quizId);
		if (quiz.availability(now) != Quiz.Availability.OPEN) {
			throw new IllegalStateException("This quiz is not open right now");
		}
		Long studentId = enrollment.getStudent().getId();
		if (attemptRepository.findByQuizIdAndStudentId(quizId, studentId).isPresent()) {
			throw new IllegalStateException("This quiz has already been attempted");
		}
		Map<Long, Long> chosen = answers == null ? Map.of() : answers;
		List<QuestionView> questions = withOptions(questionRepository.findByQuizIdOrderByOrdinalAsc(quizId));
		Map<Long, QuizQuestion> byId = questions.stream().collect(Collectors.toMap(v -> v.question().getId(), QuestionView::question));
		if (!byId.keySet().containsAll(chosen.keySet())) {
			throw new IllegalArgumentException("Answers reference a question that is not part of this quiz");
		}

		int score = 0;
		int max = 0;
		List<Object[]> graded = new ArrayList<>();
		for (QuestionView view : questions) {
			Long optionId = chosen.get(view.question().getId());
			QuizOption picked = null;
			if (optionId != null) {
				picked = view.options().stream().filter(o -> o.getId().equals(optionId)).findFirst()
						.orElseThrow(() -> new IllegalArgumentException("Option " + optionId + " does not belong to its question"));
			}
			boolean correct = picked != null && picked.isCorrect();
			int awarded = correct ? view.question().getMarks() : 0;
			score += awarded;
			max += view.question().getMarks();
			graded.add(new Object[] { view.question(), picked, correct, awarded });
		}
		QuizAttempt attempt = attemptRepository.save(new QuizAttempt(quiz, studentRepository.findById(studentId).orElseThrow(),
				personRepository.findById(child.guardianPersonId()).orElseThrow(), now, score, max));
		for (Object[] g : graded) {
			answerRepository.save(new QuizAttemptAnswer(attempt, (QuizQuestion) g[0], (QuizOption) g[1], (Boolean) g[2], (Integer) g[3]));
		}
		return attempt;
	}

	@Transactional(readOnly = true)
	public AttemptDetail attemptDetail(StudentEnrollment enrollment, Long quizId, Instant now) {
		Quiz quiz = requireOwnPublished(enrollment, quizId);
		QuizAttempt attempt = attemptRepository.findByQuizIdAndStudentId(quizId, enrollment.getStudent().getId())
				.orElseThrow(() -> new IllegalArgumentException("No attempt found for this quiz"));
		boolean showCorrect = quiz.availability(now) == Quiz.Availability.CLOSED;
		Map<Long, QuizAttemptAnswer> answers = answerRepository.findByAttemptId(attempt.getId()).stream()
				.collect(Collectors.toMap(a -> a.getQuestion().getId(), Function.identity()));
		List<AnswerDetail> details = withOptions(questionRepository.findByQuizIdOrderByOrdinalAsc(quizId)).stream().map(v -> {
			QuizAttemptAnswer a = answers.get(v.question().getId());
			return new AnswerDetail(v.question(), v.options(), a == null || a.getSelectedOption() == null ? null : a.getSelectedOption().getId(),
					a != null && a.isCorrect(), a == null ? 0 : a.getMarksAwarded());
		}).toList();
		return new AttemptDetail(attempt, details, showCorrect);
	}

	/** Every attempt the student has made, newest first - the per-student quiz history. */
	@Transactional(readOnly = true)
	public List<QuizAttempt> history(Long studentId) {
		return attemptRepository.findByStudentIdOrderBySubmittedAtDesc(studentId);
	}

	// --- helpers ---

	private Quiz find(Long quizId) {
		return quizRepository.findById(quizId).orElseThrow(() -> new IllegalArgumentException("Quiz not found: " + quizId));
	}

	private Quiz requireDraft(LearningActor actor, Long quizId) {
		Quiz quiz = find(quizId);
		actor.requireWrite(quiz.getSection().getId(), quiz.getSubject().getId());
		if (!quiz.isDraft()) {
			throw new IllegalStateException("A published quiz can no longer be changed");
		}
		return quiz;
	}

	private Quiz requireOwnPublished(StudentEnrollment enrollment, Long quizId) {
		Quiz quiz = find(quizId);
		if (quiz.isDraft() || !quiz.getSection().getId().equals(enrollment.getSection().getId())) {
			throw new IllegalArgumentException("Quiz not found: " + quizId);
		}
		return quiz;
	}

	private int nextOrdinal(Long quizId) {
		return questionRepository.findByQuizIdOrderByOrdinalAsc(quizId).stream().mapToInt(QuizQuestion::getOrdinal).max().orElse(0) + 1;
	}

	private QuizQuestion saveQuestion(Quiz quiz, Subject subject, int ordinal, String text, int marks, List<OptionInput> options,
			LearningActor actor) {
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("Question text is required");
		}
		if (marks < 1) {
			throw new IllegalArgumentException("Marks must be at least 1");
		}
		if (options == null || options.size() < 2 || options.size() > 6) {
			throw new IllegalArgumentException("A question needs between 2 and 6 options");
		}
		if (options.stream().anyMatch(o -> o.text() == null || o.text().isBlank())) {
			throw new IllegalArgumentException("Every option needs text");
		}
		if (options.stream().filter(OptionInput::correct).count() != 1) {
			throw new IllegalArgumentException("Mark exactly one option as correct");
		}
		QuizQuestion question = questionRepository.save(new QuizQuestion(quiz, subject, ordinal, text.trim(), marks,
				personRepository.findById(actor.personId()).orElseThrow()));
		int i = 1;
		for (OptionInput option : options) {
			optionRepository.save(new QuizOption(question, i++, option.text().trim(), option.correct()));
		}
		return question;
	}

	private List<QuestionView> withOptions(List<QuizQuestion> questions) {
		if (questions.isEmpty()) {
			return List.of();
		}
		Map<Long, List<QuizOption>> options = optionRepository.findByQuestionIdIn(questions.stream().map(QuizQuestion::getId).toList()).stream()
				.sorted(Comparator.comparingInt(QuizOption::getOrdinal)).collect(Collectors.groupingBy(o -> o.getQuestion().getId()));
		return questions.stream().map(q -> new QuestionView(q, options.getOrDefault(q.getId(), List.of()))).toList();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
