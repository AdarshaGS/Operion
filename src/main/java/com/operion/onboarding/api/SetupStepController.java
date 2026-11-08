package com.operion.onboarding.api;

import com.operion.authorization.RequirePermission;
import com.operion.onboarding.SetupStepService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Server-side progress of the Structure Setup wizard. Same permission as the screens it tracks (organisation
 * profile, settings and campuses are all ORGANISATION_MANAGE). */
@RestController
@RequestMapping("/api/v1/setup/structure/steps")
public class SetupStepController {

	private final SetupStepService setupStepService;

	public SetupStepController(SetupStepService setupStepService) {
		this.setupStepService = setupStepService;
	}

	@GetMapping
	@RequirePermission("ORGANISATION_MANAGE")
	public SetupStepsResponse completed() {
		return new SetupStepsResponse(setupStepService.completedStepKeys());
	}

	@PostMapping("/{stepKey}/complete")
	@RequirePermission("ORGANISATION_MANAGE")
	public SetupStepsResponse complete(@PathVariable String stepKey) {
		return new SetupStepsResponse(setupStepService.complete(stepKey));
	}
}
