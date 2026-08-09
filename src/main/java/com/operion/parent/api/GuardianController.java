package com.operion.parent.api;

import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.parent.Guardian;
import com.operion.parent.GuardianRepository;
import com.operion.parent.ParentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guardians")
public class GuardianController {

	private final ParentService parentService;
	private final GuardianRepository guardianRepository;
	private final PersonRepository personRepository;

	public GuardianController(ParentService parentService, GuardianRepository guardianRepository, PersonRepository personRepository) {
		this.parentService = parentService;
		this.guardianRepository = guardianRepository;
		this.personRepository = personRepository;
	}

	@PostMapping
	public GuardianResponse getOrCreate(@RequestBody CreateGuardianRequest request) {
		Person person = personRepository.findById(request.personId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.personId()));

		return GuardianResponse.from(parentService.getOrCreateGuardian(person, request.occupation()));
	}

	@GetMapping("/{guardianId}")
	public GuardianResponse get(@PathVariable Long guardianId) {
		Guardian guardian = guardianRepository.findById(guardianId)
				.orElseThrow(() -> new IllegalArgumentException("No guardian with id " + guardianId));
		return GuardianResponse.from(guardian);
	}
}
