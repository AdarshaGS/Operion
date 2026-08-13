package com.operion.identity.auth.api;

import java.util.List;
import java.util.Set;

public record MeResponse(
		Long userId, Long organisationId, String organisationName, String email, Long personId, String personName,
		List<String> roleNames, Set<String> permissions) {
}
