package com.operion.student.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.authorization.PermissionChecker;
import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.student.StudentPromotionService;
import com.operion.student.StudentPromotionService.Action;
import com.operion.student.StudentPromotionService.Decision;
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
	private final PermissionChecker permissionChecker;

	public PromotionController(StudentPromotionService promotionService, PermissionChecker permissionChecker) {
		this.promotionService = promotionService;
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
}
