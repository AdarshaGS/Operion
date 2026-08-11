package com.operion.authorization.api;

import java.util.Set;
import java.util.stream.Collectors;

import com.operion.authorization.Permission;
import com.operion.authorization.Role;

public record RoleResponse(Long id, String name, String description, boolean systemDefault, String status,
		Set<String> permissionCodes) {

	public static RoleResponse from(Role role) {
		Set<String> codes = role.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet());
		return new RoleResponse(role.getId(), role.getName(), role.getDescription(), role.isSystemDefault(),
				role.getStatus().name(), codes);
	}
}
