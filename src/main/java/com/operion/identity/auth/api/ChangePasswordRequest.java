package com.operion.identity.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// max 72: bcrypt rejects longer passwords (as an error, not by truncating) - see PasswordEncoder.
// currentPassword stays un-constrained beyond a cap: a null/blank one is just a wrong password (409).
public record ChangePasswordRequest(@Size(max = 256) String currentPassword,
		@NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters") String newPassword) {
}
