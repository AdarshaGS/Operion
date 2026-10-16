package com.operion.identity.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClaimInviteRequest(String organisationSlug, String token,
		@NotBlank @Size(min = 8, message = "must be at least 8 characters") String password) {
}
