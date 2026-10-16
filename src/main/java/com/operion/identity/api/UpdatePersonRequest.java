package com.operion.identity.api;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PastOrPresent;

/** Partial update: a field left out (null) keeps its stored value; an optional text field sent as "" is cleared. */
public record UpdatePersonRequest(String firstName, String lastName, @PastOrPresent LocalDate dateOfBirth,
		String gender, String phone, @Email String email, String address) {
}
