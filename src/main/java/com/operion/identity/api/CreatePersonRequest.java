package com.operion.identity.api;

import java.time.LocalDate;

public record CreatePersonRequest(String firstName, String lastName, LocalDate dateOfBirth, String gender, String phone,
		String email, String photoUrl) {
}
