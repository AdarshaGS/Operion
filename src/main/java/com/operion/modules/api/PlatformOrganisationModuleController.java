package com.operion.modules.api;

import java.util.List;

import com.operion.modules.AppModule;
import com.operion.modules.ModuleAccessService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-owner only: /api/v1/platform/** is authenticated by the separate platform
 * token plane, so an organisation Owner has no way to call this (GitHub #198).
 */
@RestController
public class PlatformOrganisationModuleController {

	private final ModuleAccessService moduleAccessService;

	public PlatformOrganisationModuleController(ModuleAccessService moduleAccessService) {
		this.moduleAccessService = moduleAccessService;
	}

	@GetMapping("/api/v1/platform/organisations/{organisationId}/modules")
	public List<OrganisationModuleResponse> list(@PathVariable Long organisationId) {
		return moduleAccessService.states(organisationId).stream().map(OrganisationModuleResponse::from).toList();
	}

	@PutMapping("/api/v1/platform/organisations/{organisationId}/modules/{code}")
	public OrganisationModuleResponse update(@PathVariable Long organisationId, @PathVariable AppModule code,
			@Valid @RequestBody UpdateOrganisationModuleRequest request) {
		return OrganisationModuleResponse.from(moduleAccessService.setEnabled(organisationId, code, request.enabled(), request.note()));
	}

	@GetMapping("/api/v1/platform/organisations/{organisationId}/modules/history")
	public List<OrganisationModuleHistoryResponse> history(@PathVariable Long organisationId) {
		return moduleAccessService.history(organisationId).stream().map(OrganisationModuleHistoryResponse::from).toList();
	}
}
