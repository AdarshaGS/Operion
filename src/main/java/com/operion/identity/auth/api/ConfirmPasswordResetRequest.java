package com.operion.identity.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// max 72: bcrypt rejects longer passwords (as an error, not by truncating) - see PasswordEncoder.
public record ConfirmPasswordResetRequest(@NotBlank @Size(max = 100) String organisationSlug, @NotBlank @Size(max = 512) String token,
		@NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters") String newPassword) {
}
