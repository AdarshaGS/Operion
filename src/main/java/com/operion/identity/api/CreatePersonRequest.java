package com.operion.identity.api;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;

public record CreatePersonRequest(@NotBlank String firstName, @NotBlank String lastName, @PastOrPresent LocalDate dateOfBirth,
		@NotBlank String gender, String phone, @Email String email, String photoUrl, String address) {
}
