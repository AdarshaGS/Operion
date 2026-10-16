package com.operion.onboarding.api;

import java.util.Map;

import com.operion.authorization.RequirePermission;
import com.operion.onboarding.SchoolStarterPackService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The opt-in "School setup template" action (see SchoolTemplateService). GET preview is
 * open to any authenticated org member, same reasoning as OrganisationConfigurationController
 * .get() - a read-only status display. POST apply is gated the same as every other
 * Organisation Settings write.
 */
@RestController
@RequestMapping("/api/v1/organisations/settings/school-template")
public class SchoolTemplateController {

	private final SchoolStarterPackService starterPackService;

	public SchoolTemplateController(SchoolStarterPackService starterPackService) {
		this.starterPackService = starterPackService;
	}

	@GetMapping("/preview")
	public SchoolTemplatePreviewResponse preview() {
		return SchoolTemplatePreviewResponse.from(starterPackService.preview());
	}

	@GetMapping("/needs")
	public StarterPackNeedsResponse needs() {
		return StarterPackNeedsResponse.from(starterPackService.needs());
	}

	/** Pure preview of which modules the answers would switch on or off - nothing is written. */
	@PostMapping("/recommend")
	public StarterPackRecommendationResponse recommend(@RequestBody StarterPackAnswersRequest request) {
		return StarterPackRecommendationResponse.from(starterPackService.recommend(answersOf(request)));
	}

	/** Body optional: with answers, also records the recommended modules (first time only); without, applies for the modules already on. */
	@PostMapping("/apply")
	@RequirePermission("ORGANISATION_MANAGE")
	public SchoolTemplateApplyResponse apply(@RequestBody(required = false) StarterPackAnswersRequest request) {
		return SchoolTemplateApplyResponse.from(starterPackService.apply(request == null ? null : answersOf(request)));
	}

	private Map<String, Boolean> answersOf(StarterPackAnswersRequest request) {
		return request == null || request.answers() == null ? Map.of() : request.answers();
	}
}
