package com.operion.identity.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(String currentPassword,
		@NotBlank @Size(min = 8, message = "must be at least 8 characters") String newPassword) {
}
