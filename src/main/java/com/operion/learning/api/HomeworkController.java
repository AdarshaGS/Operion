package com.operion.learning.api;

import java.util.List;

import com.operion.common.TenantContext;
import com.operion.learning.Homework;
import com.operion.learning.HomeworkService;
import com.operion.learning.LearningAccessService;
import com.operion.storage.AssetStorageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * No {@code @RequirePermission}: shared by admins (LEARNING_VIEW/MANAGE) and portal teachers,
 * who carry no such codes - LearningAccessService resolves which sections the caller may touch.
 */
@RestController
@RequestMapping("/api/v1/learning/homework")
public class HomeworkController {

	private final HomeworkService homeworkService;
	private final LearningAccessService accessService;
	private final AssetStorageService storage;

	public HomeworkController(HomeworkService homeworkService, LearningAccessService accessService, AssetStorageService storage) {
		this.homeworkService = homeworkService;
		this.accessService = accessService;
		this.storage = storage;
	}

	@GetMapping
	public List<HomeworkResponse> list(@RequestParam(required = false) Long sectionId) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return homeworkService.list(actor, sectionId).stream().map(v -> HomeworkResponse.from(v, storage)).toList();
	}

	@PostMapping
	public HomeworkResponse create(@RequestBody CreateHomeworkRequest request) {
		var actor = accessService.resolve(TenantContext.getActorId());
		Homework h = homeworkService.assign(actor, request.sectionId(), request.subjectId(), request.title(),
				request.description(), request.dueDate(), request.attachmentRef());
		return HomeworkResponse.from(homeworkService.viewOf(h), storage);
	}

	@PostMapping("/{id}/cancel")
	public HomeworkResponse cancel(@PathVariable Long id) {
		var actor = accessService.resolve(TenantContext.getActorId());
		Homework h = homeworkService.cancel(actor, id);
		return HomeworkResponse.from(new HomeworkService.HomeworkView(h, 0, 0, 0), storage);
	}

	@GetMapping("/{id}/submissions")
	public List<HomeworkTrackingRowResponse> submissions(@PathVariable Long id) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return homeworkService.tracking(actor, id).stream().map(r -> HomeworkTrackingRowResponse.from(r, storage)).toList();
	}

	@PostMapping("/submissions/{submissionId}/review")
	public HomeworkTrackingRowResponse.Reviewed review(@PathVariable Long submissionId, @RequestBody ReviewRequest request) {
		var actor = accessService.resolve(TenantContext.getActorId());
		var s = homeworkService.review(actor, submissionId, request.feedback());
		return new HomeworkTrackingRowResponse.Reviewed(s.getId(), s.getStatus().name(), s.getFeedback());
	}
}
