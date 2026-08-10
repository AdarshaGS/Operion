package com.operion.identity.api;

import java.time.LocalDate;

import com.operion.identity.Person;

public record PersonResponse(Long id, String firstName, String lastName, LocalDate dateOfBirth, String gender,
		String phone, String email, String photoUrl, String status) {

	static PersonResponse from(Person person) {
		return new PersonResponse(person.getId(), person.getFirstName(), person.getLastName(), person.getDateOfBirth(),
				person.getGender(), person.getPhone(), person.getEmail(), person.getPhotoUrl(), person.getStatus().name());
	}
}
