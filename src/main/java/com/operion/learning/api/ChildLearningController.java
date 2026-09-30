package com.operion.learning.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.common.TenantContext;
import com.operion.learning.ChildAccessService;
import com.operion.learning.HomeworkService;
import com.operion.learning.LearningMaterialService;
import com.operion.learning.LessonPlanService;
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
	private final AssetStorageService storage;

	public ChildLearningController(ChildAccessService childAccess, HomeworkService homeworkService,
			LearningMaterialService materialService, LessonPlanService lessonPlanService, AssetStorageService storage) {
		this.childAccess = childAccess;
		this.homeworkService = homeworkService;
		this.materialService = materialService;
		this.lessonPlanService = lessonPlanService;
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
}
