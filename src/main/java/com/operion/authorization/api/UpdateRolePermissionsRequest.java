package com.operion.authorization.api;

import java.util.Set;

public record UpdateRolePermissionsRequest(Set<String> permissionCodes) {
}
