package com.operion.onboarding.api;

import com.operion.authorization.RequirePermission;
import com.operion.onboarding.readiness.GoLiveReport;
import com.operion.onboarding.readiness.GoLiveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only go-live report; guidance, not a gate - nothing here blocks activating the year or inviting users. */
@RestController
@RequestMapping("/api/v1/organisations/settings/go-live")
public class GoLiveController {

	private final GoLiveService goLiveService;

	public GoLiveController(GoLiveService goLiveService) {
		this.goLiveService = goLiveService;
	}

	@GetMapping
	@RequirePermission("ORGANISATION_MANAGE")
	public GoLiveReport report() {
		return goLiveService.report();
	}
}
