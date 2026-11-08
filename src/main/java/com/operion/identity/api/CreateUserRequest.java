package com.operion.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(@NotBlank @Email(message = "must be a valid email address") String email, String phone,
		@NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters") String password) {
}
