package com.operion.organisation.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.organisation.rollover.AcademicYearRolloverService;
import com.operion.organisation.rollover.RolloverReport;
import com.operion.organisation.rollover.RolloverRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Copy-forward into a new academic year: list the steps, preview what they would do, then apply. */
@RestController
@RequestMapping("/api/v1/academic-years")
public class AcademicYearRolloverController {

	private final AcademicYearRolloverService rolloverService;

	public AcademicYearRolloverController(AcademicYearRolloverService rolloverService) {
		this.rolloverService = rolloverService;
	}

	public record StepResponse(String key, String label, String module) {
	}

	/** Only the steps whose module is enabled for this organisation. */
	@GetMapping("/rollover/steps")
	@RequirePermission("ORGANISATION_MANAGE")
	public List<StepResponse> steps() {
		return rolloverService.availableSteps().stream()
				.map(step -> new StepResponse(step.key(), step.label(), step.module().name())).toList();
	}

	/** Runs every selected step and rolls back, so the counts are exactly what apply would do. */
	@PostMapping("/{targetYearId}/rollover/preview")
	@RequirePermission("ORGANISATION_MANAGE")
	public RolloverReport preview(@PathVariable Long targetYearId, @RequestBody RolloverRequest request) {
		return rolloverService.preview(targetYearId, request);
	}

	@PostMapping("/{targetYearId}/rollover/apply")
	@RequirePermission("ORGANISATION_MANAGE")
	public RolloverReport apply(@PathVariable Long targetYearId, @RequestBody RolloverRequest request) {
		return rolloverService.apply(targetYearId, request);
	}
}
