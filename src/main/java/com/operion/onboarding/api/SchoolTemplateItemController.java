package com.operion.onboarding.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.onboarding.SchoolTemplateItem;
import com.operion.onboarding.SchoolTemplateItemCategory;
import com.operion.onboarding.SchoolTemplateItemService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manages the per-organisation, editable "School setup template" catalog that
 * SchoolTemplateService.apply() materializes into real rows. GET is open to any
 * authenticated member (a settings-display list, same reasoning as
 * OrganisationConfigurationController.get()); every write is gated the same as every
 * other Organisation Settings write.
 */
@RestController
@RequestMapping("/api/v1/organisations/settings/school-template/items")
public class SchoolTemplateItemController {

	private final SchoolTemplateItemService schoolTemplateItemService;

	public SchoolTemplateItemController(SchoolTemplateItemService schoolTemplateItemService) {
		this.schoolTemplateItemService = schoolTemplateItemService;
	}

	@GetMapping("/{category}")
	public List<SchoolTemplateItemResponse> list(@PathVariable SchoolTemplateItemCategory category) {
		return schoolTemplateItemService.list(category).stream().map(SchoolTemplateItemResponse::from).toList();
	}

	@PostMapping("/{category}")
	@RequirePermission("ORGANISATION_MANAGE")
	public SchoolTemplateItemResponse create(@PathVariable SchoolTemplateItemCategory category, @RequestBody SaveSchoolTemplateItemRequest request) {
		SchoolTemplateItem item = schoolTemplateItemService.create(category, request.name(), request.code(), request.description(),
				request.sequenceOrder(), request.stage(), request.categoryType(), request.minPercentage(), request.remark(),
				request.permissionCodes());
		return SchoolTemplateItemResponse.from(item);
	}

	@PutMapping("/{id}")
	@RequirePermission("ORGANISATION_MANAGE")
	public SchoolTemplateItemResponse update(@PathVariable Long id, @RequestBody SaveSchoolTemplateItemRequest request) {
		SchoolTemplateItem item = schoolTemplateItemService.update(id, request.name(), request.code(), request.description(),
				request.sequenceOrder(), request.stage(), request.categoryType(), request.minPercentage(), request.remark(),
				request.permissionCodes());
		return SchoolTemplateItemResponse.from(item);
	}

	@DeleteMapping("/{id}")
	@RequirePermission("ORGANISATION_MANAGE")
	public void delete(@PathVariable Long id) {
		schoolTemplateItemService.delete(id);
	}
}
