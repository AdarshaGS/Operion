package com.operion.authorization.api;

import java.util.List;
import java.util.Set;

import com.operion.authorization.PermissionRepository;
import com.operion.common.TenantContext;
import com.operion.modules.AppModule;
import com.operion.modules.ModuleAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only global catalog, no @RequirePermission - any authenticated org member can list
 * it (needed to build a "assign permissions to a role" picker), same as Campus/AcademicYear
 * lookups elsewhere. Nothing here is sensitive. The one org-specific cut: permissions that
 * belong to a module switched off for the caller's organisation are left out, so a school
 * without Transport never sees (or grants) transport permissions.
 */
@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

	private final PermissionRepository permissionRepository;
	private final ModuleAccessService moduleAccessService;

	public PermissionController(PermissionRepository permissionRepository, ModuleAccessService moduleAccessService) {
		this.permissionRepository = permissionRepository;
		this.moduleAccessService = moduleAccessService;
	}

	@GetMapping
	public List<PermissionResponse> list() {
		Long organisationId = TenantContext.getOrganisationId();
		Set<AppModule> disabled = organisationId == null ? Set.of() : moduleAccessService.disabledModules(organisationId);
		return permissionRepository.findAll().stream()
				.filter(permission -> AppModule.forPermissionGroup(permission.getModule()).map(module -> !disabled.contains(module)).orElse(true))
				.map(PermissionResponse::from).toList();
	}
}
