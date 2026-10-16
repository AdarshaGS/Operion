package com.operion.authorization.api;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.operion.authorization.Permission;
import com.operion.authorization.RequirePermission;
import com.operion.authorization.Role;
import com.operion.authorization.RoleRepository;
import com.operion.authorization.RoleService;
import com.operion.authorization.RoleStatus;
import com.operion.common.EnumParam;
import com.operion.common.TenantContext;
import com.operion.modules.AppModule;
import com.operion.modules.ModuleAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

	private final RoleService roleService;
	private final RoleRepository roleRepository;

	private final ModuleAccessService moduleAccessService;

	public RoleController(RoleService roleService, RoleRepository roleRepository, ModuleAccessService moduleAccessService) {
		this.roleService = roleService;
		this.roleRepository = roleRepository;
		this.moduleAccessService = moduleAccessService;
	}

	// List/get are unrestricted (any authenticated member) - needed to populate the role
	// picker on the membership-grant screen, which itself is what's gated by MEMBERSHIP_MANAGE.
	@GetMapping
	public List<RoleResponse> list() {
		return roleRepository.findAll().stream().map(this::toResponse).toList();
	}

	@GetMapping("/{id}")
	public RoleResponse get(@PathVariable Long id) {
		return toResponse(findOrThrow(id));
	}

	@PostMapping
	@RequirePermission("ROLE_MANAGE")
	public RoleResponse create(@RequestBody CreateRoleRequest request) {
		return RoleResponse.from(
				roleService.create(request.name(), request.description(), request.permissionCodes(), request.portal()));
	}

	@PostMapping("/{id}/permissions")
	@RequirePermission("ROLE_MANAGE")
	public RoleResponse updatePermissions(@PathVariable Long id, @RequestBody UpdateRolePermissionsRequest request) {
		Set<String> codes = request.permissionCodes();
		if (codes != null) {
			// Permissions of switched-off modules are invisible to the caller, so they cannot have been unticked - keep them.
			codes = new HashSet<>(codes);
			Set<AppModule> hidden = hiddenModules();
			for (Permission permission : findOrThrow(id).getPermissions()) {
				if (RoleResponse.isHidden(permission, hidden)) {
					codes.add(permission.getCode());
				}
			}
		}
		return toResponse(roleService.updatePermissions(id, codes, TenantContext.getActorId()));
	}

	@PostMapping("/{id}/status")
	@RequirePermission("ROLE_MANAGE")
	public RoleResponse changeStatus(@PathVariable Long id, @RequestBody ChangeRoleStatusRequest request) {
		return toResponse(roleService.changeStatus(id, EnumParam.parse(RoleStatus.class, request.status()), TenantContext.getActorId()));
	}

	@PostMapping("/{id}/portal")
	@RequirePermission("ROLE_MANAGE")
	public RoleResponse updatePortalFlag(@PathVariable Long id, @RequestBody UpdateRolePortalFlagRequest request) {
		return toResponse(roleService.updatePortalFlag(id, request.portal(), TenantContext.getActorId()));
	}

	private Set<AppModule> hiddenModules() {
		Long organisationId = TenantContext.getOrganisationId();
		return organisationId == null ? Set.of() : moduleAccessService.disabledModules(organisationId);
	}

	private RoleResponse toResponse(Role role) {
		return RoleResponse.from(role, hiddenModules());
	}

	private Role findOrThrow(Long id) {
		return roleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No role with id " + id));
	}
}
