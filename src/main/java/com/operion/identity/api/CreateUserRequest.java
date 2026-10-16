package com.operion.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(@NotBlank @Email(message = "must be a valid email address") String email, String phone,
		@NotBlank @Size(min = 8, message = "must be at least 8 characters") String password) {
}
