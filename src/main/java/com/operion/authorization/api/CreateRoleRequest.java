package com.operion.authorization.api;

import java.util.Set;

public record CreateRoleRequest(String name, String description, Set<String> permissionCodes) {
}
