package com.operion.learning.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.common.TenantContext;
import com.operion.learning.LearningAccessService;
import com.operion.learning.LessonPlanService;
import com.operion.learning.LessonPlanStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Not gated by a fixed permission - teachers author, LESSON_PLAN_REVIEW holders review; see LessonPlanService. */
@RestController
@RequestMapping("/api/v1/learning/lesson-plans")
public class LessonPlanController {

	private final LessonPlanService planService;
	private final LearningAccessService accessService;

	public LessonPlanController(LessonPlanService planService, LearningAccessService accessService) {
		this.planService = planService;
		this.accessService = accessService;
	}

	@GetMapping
	public List<LessonPlanResponse> list(@RequestParam(required = false) Long sectionId,
			@RequestParam(required = false) LessonPlanStatus status) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return planService.list(actor, sectionId, status).stream().map(LessonPlanResponse::from).toList();
	}

	@GetMapping("/calendar")
	public List<LessonPlanResponse> calendar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return planService.calendar(actor, from, to).stream().map(LessonPlanResponse::from).toList();
	}

	@PostMapping
	public LessonPlanResponse create(@RequestBody LessonPlanRequest r) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return LessonPlanResponse.from(planService.create(actor, r.sectionId(), r.subjectId(), r.planDate(), r.title(),
				r.objectives(), r.activities(), r.resources()));
	}

	@PutMapping("/{id}")
	public LessonPlanResponse edit(@PathVariable Long id, @RequestBody LessonPlanRequest r) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return LessonPlanResponse.from(planService.edit(actor, id, r.planDate(), r.title(), r.objectives(), r.activities(), r.resources()));
	}

	@PostMapping("/{id}/submit")
	public LessonPlanResponse submit(@PathVariable Long id) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return LessonPlanResponse.from(planService.submit(actor, id));
	}

	@PostMapping("/{id}/approve")
	public LessonPlanResponse approve(@PathVariable Long id, @RequestBody ReviewRequest request) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return LessonPlanResponse.from(planService.approve(actor, id, request.feedback()));
	}

	@PostMapping("/{id}/reject")
	public LessonPlanResponse reject(@PathVariable Long id, @RequestBody ReviewRequest request) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return LessonPlanResponse.from(planService.reject(actor, id, request.feedback()));
	}
}
