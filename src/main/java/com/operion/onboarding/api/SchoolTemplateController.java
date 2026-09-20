package com.operion.onboarding.api;

import com.operion.authorization.RequirePermission;
import com.operion.onboarding.SchoolTemplateResult;
import com.operion.onboarding.SchoolTemplateService;
import com.operion.onboarding.SchoolTemplateStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

	private final SchoolTemplateService schoolTemplateService;

	public SchoolTemplateController(SchoolTemplateService schoolTemplateService) {
		this.schoolTemplateService = schoolTemplateService;
	}

	@GetMapping("/preview")
	public SchoolTemplatePreviewResponse preview() {
		return SchoolTemplatePreviewResponse.from(schoolTemplateService.preview());
	}

	@PostMapping("/apply")
	@RequirePermission("ORGANISATION_MANAGE")
	public SchoolTemplateApplyResponse apply() {
		return SchoolTemplateApplyResponse.from(schoolTemplateService.apply());
	}
}
