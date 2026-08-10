package com.operion.identity.api;

import java.util.List;

import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No dedicated service - Person has no business rules beyond "save the row" (unlike
 * Student/Guardian/StaffProfile, which each own real validation on top of their own
 * Person FK), so a service class here would be pure ceremony wrapping repository.save.
 */
@RestController
@RequestMapping("/api/v1/persons")
public class PersonController {

	private final PersonRepository personRepository;

	public PersonController(PersonRepository personRepository) {
		this.personRepository = personRepository;
	}

	@PostMapping
	public PersonResponse create(@RequestBody CreatePersonRequest request) {
		Person person = new Person(request.firstName(), request.lastName());
		person.setDateOfBirth(request.dateOfBirth());
		person.setGender(request.gender());
		person.setPhone(request.phone());
		person.setEmail(request.email());
		person.setPhotoUrl(request.photoUrl());
		return PersonResponse.from(personRepository.save(person));
	}

	@GetMapping
	public List<PersonResponse> list() {
		return personRepository.findAll().stream().map(PersonResponse::from).toList();
	}

	@GetMapping("/{id}")
	public PersonResponse get(@PathVariable Long id) {
		return PersonResponse.from(findOrThrow(id));
	}

	private Person findOrThrow(Long id) {
		return personRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No person with id " + id));
	}
}
