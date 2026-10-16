package com.operion.learning.api;

import java.time.Instant;
import java.util.List;

import com.operion.common.TenantContext;
import com.operion.learning.LearningAccessService;
import com.operion.learning.QuizService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Not permission-gated - shared by admins and portal teachers, scoped by LearningAccessService. */
@RestController
@RequestMapping("/api/v1/learning")
public class QuizController {

	private final QuizService quizService;
	private final LearningAccessService accessService;

	public QuizController(QuizService quizService, LearningAccessService accessService) {
		this.quizService = quizService;
		this.accessService = accessService;
	}

	@GetMapping("/quizzes")
	public List<QuizResponse> list(@RequestParam(required = false) Long sectionId) {
		var actor = accessService.resolve(TenantContext.getActorId());
		Instant now = Instant.now();
		return quizService.list(actor, sectionId).stream().map(v -> QuizResponse.from(v, now)).toList();
	}

	@PostMapping("/quizzes")
	public QuizResponse create(@RequestBody CreateQuizRequest r) {
		var actor = accessService.resolve(TenantContext.getActorId());
		var quiz = quizService.createQuiz(actor, r.sectionId(), r.subjectId(), r.title(), r.instructions(), r.opensAt(), r.closesAt());
		return QuizResponse.from(quizService.viewOf(quiz), Instant.now());
	}

	@GetMapping("/quizzes/{id}/questions")
	public List<QuestionResponse> questions(@PathVariable Long id) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return quizService.questions(actor, id).stream().map(QuestionResponse::from).toList();
	}

	@PostMapping("/quizzes/{id}/questions")
	public QuestionResponse addQuestion(@PathVariable Long id, @RequestBody QuestionRequest r) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return QuestionResponse.from(quizService.addQuestion(actor, id, r.text(), r.marks(), r.options()));
	}

	@PostMapping("/quizzes/{id}/questions/from-bank/{bankQuestionId}")
	public QuestionResponse addFromBank(@PathVariable Long id, @PathVariable Long bankQuestionId) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return QuestionResponse.from(quizService.addFromBank(actor, id, bankQuestionId));
	}

	@DeleteMapping("/quizzes/{id}/questions/{questionId}")
	public void removeQuestion(@PathVariable Long id, @PathVariable Long questionId) {
		var actor = accessService.resolve(TenantContext.getActorId());
		quizService.removeQuestion(actor, id, questionId);
	}

	@PostMapping("/quizzes/{id}/publish")
	public QuizResponse publish(@PathVariable Long id) {
		var actor = accessService.resolve(TenantContext.getActorId());
		var quiz = quizService.publish(actor, id);
		return QuizResponse.from(quizService.viewOf(quiz), Instant.now());
	}

	@GetMapping("/quizzes/{id}/results")
	public List<QuizResultRowResponse> results(@PathVariable Long id) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return quizService.results(actor, id).stream().map(QuizResultRowResponse::from).toList();
	}

	@GetMapping("/question-bank")
	public List<QuestionResponse> bank(@RequestParam(required = false) Long subjectId) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return quizService.bank(actor, subjectId).stream().map(QuestionResponse::from).toList();
	}

	@PostMapping("/question-bank")
	public QuestionResponse addBankQuestion(@RequestBody QuestionRequest r) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return QuestionResponse.from(quizService.addBankQuestion(actor, r.subjectId(), r.text(), r.marks(), r.options()));
	}

	@DeleteMapping("/question-bank/{id}")
	public void deleteBankQuestion(@PathVariable Long id) {
		var actor = accessService.resolve(TenantContext.getActorId());
		quizService.deleteBankQuestion(actor, id);
	}
}
