package com.operion.student.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.authorization.PermissionChecker;
import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.student.StudentPromotionPreviewService;
import com.operion.student.StudentPromotionService;
import com.operion.student.StudentPromotionService.Action;
import com.operion.student.StudentPromotionService.Decision;
import com.operion.student.StudentPromotionPreviewService.ClassPreview;
import com.operion.student.StudentPromotionService.Outcome;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Batch year-end promotion - see StudentPromotionService. Graduating is an exit, so a batch
 * containing any GRADUATE decision also needs STUDENT_EXIT_MANAGE. */
@RestController
@RequestMapping("/api/v1/student-promotions")
@RequirePermission("STUDENT_ENROLLMENT_MANAGE")
public class PromotionController {

	public record PromotionRequest(Long targetAcademicYearId, LocalDate promotionDate, List<Decision> decisions) {
	}

	private final StudentPromotionService promotionService;
	private final StudentPromotionPreviewService previewService;
	private final PermissionChecker permissionChecker;

	public PromotionController(StudentPromotionService promotionService, StudentPromotionPreviewService previewService,
			PermissionChecker permissionChecker) {
		this.promotionService = promotionService;
		this.previewService = previewService;
		this.permissionChecker = permissionChecker;
	}

	@PostMapping
	public List<Outcome> promote(@RequestBody PromotionRequest request) {
		Long actorId = TenantContext.getActorId();
		if (request.decisions() != null && request.decisions().stream().anyMatch(d -> d.action() == Action.GRADUATE)) {
			permissionChecker.require(actorId, "STUDENT_EXIT_MANAGE");
		}
		return promotionService.apply(request.targetAcademicYearId(), request.promotionDate(), request.decisions(), actorId);
	}

	/** Read-only suggestions for the wizard - writes nothing; the admin still submits explicit decisions to {@link #promote}. */
	@PostMapping("/preview")
	public List<ClassPreview> preview(@RequestBody StudentPromotionPreviewService.Request request) {
		return previewService.preview(request);
	}
}
