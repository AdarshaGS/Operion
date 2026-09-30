package com.operion.learning;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.examination.MarksEntry;
import com.operion.examination.MarksEntryRepository;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leaderboards and reward-granting (#231). Student boards are per section and kept as separate
 * tracks (quiz vs academic assessment vs rewards) - they are never blended into one score. The
 * teacher board is activity-based (there is no rating data to rank on), with the weights below.
 */
@Service
@Transactional
public class LeaderboardService {

	public enum Track {
		QUIZ,
		ACADEMIC,
		REWARD
	}

	public record BoardEntry(StudentEnrollment enrollment, double score, int rank) {
	}

	/** A student's own position on one track; rank is null when they have no score on it yet. */
	public record Standing(Integer rank, int of, Double score) {
	}

	public record TeacherEntry(Person person, int points, int homeworkAssigned, int quizzesPublished, int materialsShared,
			int lessonPlansApproved, int sheetsReleased, int rank) {
	}

	static final int POINTS_HOMEWORK = 2;
	static final int POINTS_QUIZ = 3;
	static final int POINTS_MATERIAL = 1;
	static final int POINTS_LESSON_PLAN = 3;
	static final int POINTS_SHEET = 2;
	static final int MAX_REWARD_POINTS = 100;

	private final StudentEnrollmentRepository enrollmentRepository;
	private final QuizRepository quizRepository;
	private final QuizAttemptRepository attemptRepository;
	private final MarksEntryRepository marksEntryRepository;
	private final StudentRewardRepository rewardRepository;
	private final HomeworkRepository homeworkRepository;
	private final LearningMaterialRepository materialRepository;
	private final LessonPlanRepository lessonPlanRepository;
	private final AnswerSheetRepository answerSheetRepository;
	private final PersonRepository personRepository;

	public LeaderboardService(StudentEnrollmentRepository enrollmentRepository, QuizRepository quizRepository,
			QuizAttemptRepository attemptRepository, MarksEntryRepository marksEntryRepository, StudentRewardRepository rewardRepository,
			HomeworkRepository homeworkRepository, LearningMaterialRepository materialRepository, LessonPlanRepository lessonPlanRepository,
			AnswerSheetRepository answerSheetRepository, PersonRepository personRepository) {
		this.enrollmentRepository = enrollmentRepository;
		this.quizRepository = quizRepository;
		this.attemptRepository = attemptRepository;
		this.marksEntryRepository = marksEntryRepository;
		this.rewardRepository = rewardRepository;
		this.homeworkRepository = homeworkRepository;
		this.materialRepository = materialRepository;
		this.lessonPlanRepository = lessonPlanRepository;
		this.answerSheetRepository = answerSheetRepository;
		this.personRepository = personRepository;
	}

	@Transactional(readOnly = true)
	public List<BoardEntry> studentBoard(LearningActor actor, Long sectionId, Track track) {
		actor.requireReadSection(sectionId);
		return board(sectionId, track);
	}

	/** Where one child stands in their section on every track, without exposing anyone else. */
	@Transactional(readOnly = true)
	public Map<Track, Standing> standing(StudentEnrollment enrollment) {
		Map<Track, Standing> result = new EnumMap<>(Track.class);
		for (Track track : Track.values()) {
			List<BoardEntry> entries = board(enrollment.getSection().getId(), track);
			BoardEntry mine = entries.stream().filter(e -> e.enrollment().getId().equals(enrollment.getId())).findFirst().orElse(null);
			result.put(track, new Standing(mine == null ? null : mine.rank(), entries.size(), mine == null ? null : mine.score()));
		}
		return result;
	}

	/** Current students of a section the caller can read - the pick list for granting a reward. */
	@Transactional(readOnly = true)
	public List<StudentEnrollment> sectionStudents(LearningActor actor, Long sectionId) {
		actor.requireReadSection(sectionId);
		return enrollmentRepository.findBySectionIdAndCurrentTrue(sectionId).stream()
				.sorted(Comparator.comparing((StudentEnrollment e) -> e.getRollNumber() == null ? Integer.MAX_VALUE : e.getRollNumber())).toList();
	}

	public StudentReward grant(LearningActor actor, Long enrollmentId, String title, int points, String reason, Instant now) {
		StudentEnrollment enrollment = enrollmentRepository.findById(enrollmentId)
				.orElseThrow(() -> new IllegalArgumentException("Enrollment not found: " + enrollmentId));
		if (!enrollment.isCurrent()) {
			throw new IllegalArgumentException("Rewards can only be granted to currently enrolled students");
		}
		if (!actor.manager() && !actor.teaches(enrollment.getSection().getId())) {
			throw new AuthorizationDeniedException("You do not teach this student's section");
		}
		if (title == null || title.isBlank() || title.trim().length() > 200) {
			throw new IllegalArgumentException("A reward needs a title of up to 200 characters");
		}
		if (points < 1 || points > MAX_REWARD_POINTS) {
			throw new IllegalArgumentException("Points must be between 1 and " + MAX_REWARD_POINTS);
		}
		if (reason != null && reason.length() > 1000) {
			throw new IllegalArgumentException("The reason is too long");
		}
		return rewardRepository.save(new StudentReward(enrollment.getStudent(), title.trim(),
				points, reason == null || reason.isBlank() ? null : reason.trim(), personRepository.findById(actor.personId()).orElseThrow(), now));
	}

	@Transactional(readOnly = true)
	public List<StudentReward> rewardsInSection(LearningActor actor, Long sectionId) {
		actor.requireReadSection(sectionId);
		List<Long> studentIds = enrollmentRepository.findBySectionIdAndCurrentTrue(sectionId).stream().map(e -> e.getStudent().getId()).toList();
		return studentIds.isEmpty() ? List.of() : rewardRepository.findByStudentIdInOrderByGrantedAtDescIdDesc(studentIds);
	}

	@Transactional(readOnly = true)
	public List<StudentReward> rewardsOf(Long studentId) {
		return rewardRepository.findByStudentIdOrderByGrantedAtDescIdDesc(studentId);
	}

	/** Activity-based ranking of teaching staff; admin-only, since it compares colleagues. */
	@Transactional(readOnly = true)
	public List<TeacherEntry> teacherBoard(LearningActor actor) {
		if (!actor.seesAllSections()) {
			throw new AuthorizationDeniedException("Only administrators can view the teacher leaderboard");
		}
		Map<Long, int[]> tally = new HashMap<>();
		Map<Long, Person> people = new HashMap<>();
		// index: 0 homework, 1 quizzes, 2 materials, 3 approved lesson plans, 4 released sheets
		homeworkRepository.findAll().forEach(h -> bump(tally, people, h.getAssignedBy(), 0));
		quizRepository.findAll().stream().filter(q -> q.getStatus() == QuizStatus.PUBLISHED).forEach(q -> bump(tally, people, q.getAuthor(), 1));
		materialRepository.findAll().stream().filter(m -> m.getStatus() == MaterialStatus.ACTIVE).forEach(m -> bump(tally, people, m.getUploadedBy(), 2));
		lessonPlanRepository.findAll().stream().filter(p -> p.getStatus() == LessonPlanStatus.APPROVED).forEach(p -> bump(tally, people, p.getAuthor(), 3));
		answerSheetRepository.findAll().stream().filter(s -> s.getStatus() == AnswerSheetStatus.RELEASED && s.getMarkedBy() != null)
				.forEach(s -> bump(tally, people, s.getMarkedBy(), 4));
		List<TeacherEntry> unranked = new ArrayList<>();
		tally.forEach((personId, c) -> unranked.add(new TeacherEntry(people.get(personId),
				c[0] * POINTS_HOMEWORK + c[1] * POINTS_QUIZ + c[2] * POINTS_MATERIAL + c[3] * POINTS_LESSON_PLAN + c[4] * POINTS_SHEET,
				c[0], c[1], c[2], c[3], c[4], 0)));
		unranked.sort(Comparator.comparingInt(TeacherEntry::points).reversed().thenComparing(e -> e.person().getLastName()));
		List<TeacherEntry> ranked = new ArrayList<>();
		for (int i = 0; i < unranked.size(); i++) {
			TeacherEntry e = unranked.get(i);
			int rank = i > 0 && unranked.get(i - 1).points() == e.points() ? ranked.get(i - 1).rank() : i + 1;
			ranked.add(new TeacherEntry(e.person(), e.points(), e.homeworkAssigned(), e.quizzesPublished(), e.materialsShared(),
					e.lessonPlansApproved(), e.sheetsReleased(), rank));
		}
		return ranked;
	}

	private static void bump(Map<Long, int[]> tally, Map<Long, Person> people, Person person, int index) {
		people.putIfAbsent(person.getId(), person);
		tally.computeIfAbsent(person.getId(), k -> new int[5])[index]++;
	}

	private List<BoardEntry> board(Long sectionId, Track track) {
		List<StudentEnrollment> enrollments = enrollmentRepository.findBySectionIdAndCurrentTrue(sectionId);
		Map<Long, Double> scores = switch (track) {
			case QUIZ -> quizScores(sectionId);
			case ACADEMIC -> academicScores(sectionId);
			case REWARD -> rewardScores(enrollments);
		};
		List<StudentEnrollment> scored = enrollments.stream().filter(e -> scores.containsKey(e.getStudent().getId()))
				.sorted(Comparator.comparingDouble((StudentEnrollment e) -> scores.get(e.getStudent().getId())).reversed()
						.thenComparing(e -> e.getRollNumber() == null ? Integer.MAX_VALUE : e.getRollNumber()))
				.toList();
		List<BoardEntry> result = new ArrayList<>();
		for (int i = 0; i < scored.size(); i++) {
			double score = scores.get(scored.get(i).getStudent().getId());
			int rank = i > 0 && result.get(i - 1).score() == score ? result.get(i - 1).rank() : i + 1;
			result.add(new BoardEntry(scored.get(i), score, rank));
		}
		return result;
	}

	/** Average quiz percentage across the student's attempts at published quizzes of the section. */
	private Map<Long, Double> quizScores(Long sectionId) {
		List<Long> quizIds = quizRepository.findBySectionIdAndStatusOrderByOpensAtDescIdDesc(sectionId, QuizStatus.PUBLISHED).stream().map(Quiz::getId).toList();
		if (quizIds.isEmpty()) {
			return Map.of();
		}
		return attemptRepository.findByQuizIdIn(quizIds).stream()
				.collect(Collectors.groupingBy(a -> a.getStudent().getId(), Collectors.averagingDouble(QuizAttempt::percentage)));
	}

	/** Total marks obtained over total maximum marks across the exams the student sat (absences excluded). */
	private Map<Long, Double> academicScores(Long sectionId) {
		Map<Long, double[]> sums = new HashMap<>();
		for (MarksEntry m : marksEntryRepository.findByStudentEnrollmentSectionId(sectionId)) {
			if (m.isAbsent()) {
				continue;
			}
			double[] s = sums.computeIfAbsent(m.getStudentEnrollment().getStudent().getId(), k -> new double[2]);
			s[0] += m.getMarksObtained();
			s[1] += m.getExamSchedule().getMaxMarks();
		}
		Map<Long, Double> result = new HashMap<>();
		sums.forEach((studentId, s) -> {
			if (s[1] > 0) {
				result.put(studentId, s[0] * 100.0 / s[1]);
			}
		});
		return result;
	}

	private Map<Long, Double> rewardScores(List<StudentEnrollment> enrollments) {
		List<Long> studentIds = enrollments.stream().map(e -> e.getStudent().getId()).toList();
		if (studentIds.isEmpty()) {
			return Map.of();
		}
		return rewardRepository.findByStudentIdInOrderByGrantedAtDescIdDesc(studentIds).stream()
				.collect(Collectors.groupingBy(r -> r.getStudent().getId(), Collectors.summingDouble(StudentReward::getPoints)));
	}
}
