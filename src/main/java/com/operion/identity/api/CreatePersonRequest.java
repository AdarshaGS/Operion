package com.operion.identity.api;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreatePersonRequest(@NotBlank String firstName, @NotBlank String lastName, LocalDate dateOfBirth,
		String gender, String phone, @Email String email, String photoUrl, String address) {
}
