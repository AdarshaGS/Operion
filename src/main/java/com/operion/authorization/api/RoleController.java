package com.operion.authorization.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.authorization.Role;
import com.operion.authorization.RoleRepository;
import com.operion.authorization.RoleService;
import com.operion.authorization.RoleStatus;
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

	public RoleController(RoleService roleService, RoleRepository roleRepository) {
		this.roleService = roleService;
		this.roleRepository = roleRepository;
	}

	// List/get are unrestricted (any authenticated member) - needed to populate the role
	// picker on the membership-grant screen, which itself is what's gated by MEMBERSHIP_MANAGE.
	@GetMapping
	public List<RoleResponse> list() {
		return roleRepository.findAll().stream().map(RoleResponse::from).toList();
	}

	@GetMapping("/{id}")
	public RoleResponse get(@PathVariable Long id) {
		return RoleResponse.from(findOrThrow(id));
	}

	@PostMapping
	@RequirePermission("ROLE_MANAGE")
	public RoleResponse create(@RequestBody CreateRoleRequest request) {
		return RoleResponse.from(roleService.create(request.name(), request.description(), request.permissionCodes()));
	}

	@PostMapping("/{id}/permissions")
	@RequirePermission("ROLE_MANAGE")
	public RoleResponse updatePermissions(@PathVariable Long id, @RequestBody UpdateRolePermissionsRequest request) {
		return RoleResponse.from(roleService.updatePermissions(id, request.permissionCodes()));
	}

	@PostMapping("/{id}/status")
	@RequirePermission("ROLE_MANAGE")
	public RoleResponse changeStatus(@PathVariable Long id, @RequestBody ChangeRoleStatusRequest request) {
		return RoleResponse.from(roleService.changeStatus(id, RoleStatus.valueOf(request.status())));
	}

	private Role findOrThrow(Long id) {
		return roleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No role with id " + id));
	}
}
