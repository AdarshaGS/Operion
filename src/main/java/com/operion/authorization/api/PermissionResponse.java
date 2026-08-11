package com.operion.authorization.api;

import com.operion.authorization.Permission;

public record PermissionResponse(Long id, String code, String module, String description) {

	public static PermissionResponse from(Permission permission) {
		return new PermissionResponse(permission.getId(), permission.getCode(), permission.getModule(), permission.getDescription());
	}
}
