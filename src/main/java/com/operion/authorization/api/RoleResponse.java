package com.operion.authorization.api;

import java.util.Set;
import java.util.stream.Collectors;

import com.operion.authorization.Permission;
import com.operion.authorization.Role;
import com.operion.modules.AppModule;

public record RoleResponse(Long id, String name, String description, boolean systemDefault, boolean portal, String status,
		Set<String> permissionCodes) {

	public static RoleResponse from(Role role) {
		return from(role, Set.of());
	}

	/** {@code hiddenModules}: modules switched off for the organisation. Their permissions are not in the permission
	 * catalogue the organisation sees, so a role does not list them either - the role keeps holding them. */
	public static RoleResponse from(Role role, Set<AppModule> hiddenModules) {
		Set<String> codes = role.getPermissions().stream()
				.filter(permission -> !isHidden(permission, hiddenModules))
				.map(Permission::getCode).collect(Collectors.toSet());
		return new RoleResponse(role.getId(), role.getName(), role.getDescription(), role.isSystemDefault(), role.isPortal(),
				role.getStatus().name(), codes);
	}

	static boolean isHidden(Permission permission, Set<AppModule> hiddenModules) {
		return AppModule.forPermissionGroup(permission.getModule()).map(hiddenModules::contains).orElse(false);
	}
}
