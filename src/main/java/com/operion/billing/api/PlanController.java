package com.operion.billing.api;

import java.util.List;

import com.operion.billing.BillingService;
import com.operion.billing.PlanRepository;
import com.operion.billing.PlanStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No @RequirePermission anywhere on this path - gated purely by being under
 * /api/v1/platform/**, reachable only via a platform-admin token (PlatformAuthenticationInterceptor). */
@RestController
@RequestMapping("/api/v1/platform/plans")
public class PlanController {

	private final BillingService billingService;
	private final PlanRepository planRepository;

	public PlanController(BillingService billingService, PlanRepository planRepository) {
		this.billingService = billingService;
		this.planRepository = planRepository;
	}

	@PostMapping
	public PlanResponse create(@RequestBody CreatePlanRequest request) {
		return PlanResponse.from(billingService.createPlan(request.code(), request.name(), request.pricePerStudentPerYear()));
	}

	@GetMapping
	public List<PlanResponse> list() {
		return planRepository.findByStatus(PlanStatus.ACTIVE).stream().map(PlanResponse::from).toList();
	}

	@PostMapping("/{id}/status")
	public PlanResponse changeStatus(@PathVariable Long id, @RequestBody ChangePlanStatusRequest request) {
		return PlanResponse.from(billingService.changePlanStatus(id, PlanStatus.valueOf(request.status())));
	}
}
