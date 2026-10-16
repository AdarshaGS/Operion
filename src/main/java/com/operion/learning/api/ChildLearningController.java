package com.operion.learning.api;

import com.operion.common.InvalidRequestException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import com.operion.common.TenantContext;
import com.operion.learning.AnswerSheetService;
import com.operion.learning.ChildAccessService;
import com.operion.learning.HomeworkService;
import com.operion.learning.LeaderboardService;
import com.operion.learning.LearningMaterialService;
import com.operion.learning.LessonPlanService;
import com.operion.learning.QuizService;
import com.operion.storage.AssetStorageService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guardian-side learning features, acting for a linked child (students have no login of their
 * own). Not permission-gated - ChildAccessService verifies the StudentGuardian link first,
 * same rule as GuardianPortalController.
 */
@RestController
@RequestMapping("/api/v1/me/children/{studentId}")
public class ChildLearningController {

	private final ChildAccessService childAccess;
	private final HomeworkService homeworkService;
	private final LearningMaterialService materialService;
	private final LessonPlanService lessonPlanService;
	private final QuizService quizService;
	private final AnswerSheetService answerSheetService;
	private final LeaderboardService leaderboardService;
	private final AssetStorageService storage;

	public ChildLearningController(ChildAccessService childAccess, HomeworkService homeworkService,
			LearningMaterialService materialService, LessonPlanService lessonPlanService, QuizService quizService, AnswerSheetService answerSheetService, LeaderboardService leaderboardService, AssetStorageService storage) {
		this.childAccess = childAccess;
		this.homeworkService = homeworkService;
		this.materialService = materialService;
		this.lessonPlanService = lessonPlanService;
		this.quizService = quizService;
		this.answerSheetService = answerSheetService;
		this.leaderboardService = leaderboardService;
		this.storage = storage;
	}

	@GetMapping("/homework")
	public List<ChildHomeworkResponse> homework(@PathVariable Long studentId) {
		var child = childAccess.resolve(TenantContext.getActorId(), studentId);
		var submissions = homeworkService.submissionsOfChild(studentId);
		return homeworkService.forChild(child.enrollment()).stream()
				.map(h -> ChildHomeworkResponse.from(h, submissions.get(h.getId()), storage)).toList();
	}

	@PostMapping("/homework/{homeworkId}/submission")
	public ChildHomeworkResponse submitHomework(@PathVariable Long studentId, @PathVariable Long homeworkId,
			@RequestBody SubmitHomeworkRequest request) {
		var child = childAccess.resolve(TenantContext.getActorId(), studentId);
		var submission = homeworkService.submitForChild(child, homeworkId, request.text(), request.attachmentRef());
		return ChildHomeworkResponse.from(submission.getHomework(), submission, storage);
	}

	@GetMapping("/materials")
	public List<MaterialResponse> materials(@PathVariable Long studentId) {
		var child = childAccess.resolve(TenantContext.getActorId(), studentId);
		return materialService.forChild(child.enrollment()).stream().map(m -> MaterialResponse.from(m, storage)).toList();
	}

	/** Approved lesson plans for the child's section - the same calendar feed teachers and admins see. */
	@GetMapping("/lesson-plans")
	public List<LessonPlanResponse> lessonPlans(@PathVariable Long studentId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		var child = childAccess.resolve(TenantContext.getActorId(), studentId);
		return lessonPlanService.calendarForChild(child.enrollment(), from, to).stream().map(LessonPlanResponse::from).toList();
	}

	@GetMapping("/quizzes")
	public List<ChildQuizResponses.ChildQuiz> quizzes(@PathVariable Long studentId) {
		var child = childAccess.resolve(TenantContext.getActorId(), studentId);
		return quizService.forChild(child.enrollment(), Instant.now()).stream().map(ChildQuizResponses.ChildQuiz::from).toList();
	}

	/** Only while the quiz is open and not yet attempted; the DTO carries no correctness. */
	@GetMapping("/quizzes/{quizId}/questions")
	public List<ChildQuizResponses.AttemptQuestion> quizQuestions(@PathVariable Long studentId, @PathVariable Long quizId) {
		var child = childAccess.resolve(TenantContext.getActorId(), studentId);
		return quizService.questionsToAttempt(child.enrollment(), quizId, Instant.now()).stream()
				.map(ChildQuizResponses.AttemptQuestion::from).toList();
	}

	@PostMapping("/quizzes/{quizId}/attempt")
	public ChildQuizResponses.AttemptSummary attemptQuiz(@PathVariable Long studentId, @PathVariable Long quizId,
			@RequestBody ChildQuizResponses.AttemptRequest request) {
		var child = childAccess.resolve(TenantContext.getActorId(), studentId);
		Map<Long, Long> answers = new HashMap<>();
		if (request.answers() != null) {
			for (var a : request.answers()) {
				if (a.optionId() != null && answers.put(a.questionId(), a.optionId()) != null) {
					throw new InvalidRequestException("Question " + a.questionId() + " was answered more than once");
				}
			}
		}
		return ChildQuizResponses.AttemptSummary.from(quizService.submitAttempt(child, quizId, answers, Instant.now()));
	}

	@GetMapping("/quizzes/{quizId}/result")
	public ChildQuizResponses.AttemptResult quizResult(@PathVariable Long studentId, @PathVariable Long quizId) {
		var child = childAccess.resolve(TenantContext.getActorId(), studentId);
		return ChildQuizResponses.AttemptResult.from(quizService.attemptDetail(child.enrollment(), quizId, Instant.now()));
	}

	/** The child's quiz history across every quiz, newest first. */
	@GetMapping("/quiz-history")
	public List<ChildQuizResponses.AttemptSummary> quizHistory(@PathVariable Long studentId) {
		childAccess.resolve(TenantContext.getActorId(), studentId);
		return quizService.history(studentId).stream().map(ChildQuizResponses.AttemptSummary::from).toList();
	}

	/** Marked answer sheets the teacher has released - never a sheet still being marked. */
	@GetMapping("/answer-sheets")
	public List<AnswerSheetResponses.SheetResponse> answerSheets(@PathVariable Long studentId) {
		childAccess.resolve(TenantContext.getActorId(), studentId);
		return answerSheetService.releasedForStudent(studentId).stream().map(d -> AnswerSheetResponses.SheetResponse.from(d, storage)).toList();
	}

	@GetMapping("/standing")
	public LeaderboardResponses.ChildStandingResponse standing(@PathVariable Long studentId) {
		var child = childAccess.resolve(TenantContext.getActorId(), studentId);
		return LeaderboardResponses.ChildStandingResponse.from(leaderboardService.standing(child.enrollment()), leaderboardService.rewardsOf(studentId));
	}
}
