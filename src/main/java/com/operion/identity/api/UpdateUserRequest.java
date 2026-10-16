package com.operion.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(@NotBlank @Email(message = "must be a valid email address") String email, String phone) {
}
