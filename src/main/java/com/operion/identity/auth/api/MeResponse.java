package com.operion.identity.auth.api;

import java.util.List;
import java.util.Set;

public record MeResponse(
		Long userId, Long organisationId, String organisationName, String email, Long personId, String personName,
		String firstName, String lastName, String campusName, String status, List<String> roleNames,
		List<RoleSummary> roles, Set<String> permissions) {
}
