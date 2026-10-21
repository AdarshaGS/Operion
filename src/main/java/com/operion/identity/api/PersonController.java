package com.operion.identity.api;

import java.util.List;
import java.util.function.Function;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.PermissionChecker;
import com.operion.authorization.PortalSessionPolicy;
import com.operion.authorization.RequirePermission;
import com.operion.common.InvalidRequestException;
import com.operion.common.TenantContext;
import com.operion.identity.Genders;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
	private final PortalSessionPolicy portalSessionPolicy;
	private final PermissionChecker permissionChecker;

	/** Phone, email, address and date of birth go only to roles that already see people's records elsewhere; everyone
	 * else (a driver, a librarian picking a borrower) gets names and photos, which is all the pickers need (SEC-M6). */
	private static final List<String> CONTACT_VIEW_CODES = List.of("STUDENT_VIEW", "STUDENT_MANAGE", "GUARDIAN_VIEW",
			"GUARDIAN_MANAGE", "HR_VIEW", "HR_STAFF_MANAGE", "MEMBERSHIP_VIEW", "MEMBERSHIP_MANAGE", "INVENTORY_CUSTOMER_MANAGE");

	public PersonController(PersonRepository personRepository, PortalSessionPolicy portalSessionPolicy,
			PermissionChecker permissionChecker) {
		this.personRepository = personRepository;
		this.portalSessionPolicy = portalSessionPolicy;
		this.permissionChecker = permissionChecker;
	}

	/** Persons are created from several modules (students, guardians, staff, members), so any of those manage permissions suffices. */
	@PostMapping
	@RequirePermission(anyOf = { "STUDENT_MANAGE", "GUARDIAN_MANAGE", "HR_STAFF_MANAGE", "MEMBERSHIP_MANAGE" })
	public PersonResponse create(@Valid @RequestBody CreatePersonRequest request) {
		Person person = new Person(request.firstName(), request.lastName());
		person.setDateOfBirth(request.dateOfBirth());
		person.setGender(Genders.validate(request.gender()));
		person.setPhone(request.phone());
		person.setEmail(request.email());
		person.setPhotoUrl(request.photoUrl());
		person.setAddress(request.address());
		return PersonResponse.from(personRepository.save(person));
	}

	/** Reads stay open to staff (teacher pickers, attendance and marks screens all list
	 * persons), but a portal-only session (guardian) never gets the org-wide directory,
	 * and contact details are limited to CONTACT_VIEW_CODES holders. */
	@GetMapping
	public List<PersonResponse> list() {
		if (portalSessionPolicy.isPortalOnly(TenantContext.getActorId())) {
			throw new AuthorizationDeniedException("The person directory is not available to portal users");
		}
		Function<Person, PersonResponse> view = seesContactDetails() ? PersonResponse::from : PersonResponse::nameOnly;
		return personRepository.findAll().stream().map(view).toList();
	}

	private boolean seesContactDetails() {
		return permissionChecker.holdsAny(TenantContext.getActorId(), CONTACT_VIEW_CODES);
	}

	/** A portal session still needs names (message threads, teacher on a child's
	 * timetable), so it gets the name/photo only - never phone, email, address or DOB. */
	@GetMapping("/{id}")
	public PersonResponse get(@PathVariable Long id) {
		Person person = findOrThrow(id);
		if (portalSessionPolicy.isPortalOnly(TenantContext.getActorId()) || !seesContactDetails()) {
			return PersonResponse.nameOnly(person);
		}
		return PersonResponse.from(person);
	}

	/** Narrow single-field update (#115) rather than the general edit endpoint below -
	 * upload needs a real file, which the general endpoint's JSON body can't carry. */
	@PatchMapping("/{id}/photo")
	@RequirePermission(anyOf = { "STUDENT_MANAGE", "GUARDIAN_MANAGE", "HR_STAFF_MANAGE", "MEMBERSHIP_MANAGE" })
	public PersonResponse updatePhoto(@PathVariable Long id, @RequestBody UpdatePersonPhotoRequest request) {
		Person person = findOrThrow(id);
		person.setPhotoUrl(request.photoUrl());
		return PersonResponse.from(personRepository.save(person));
	}

	@PatchMapping("/{id}")
	@RequirePermission(anyOf = { "STUDENT_MANAGE", "GUARDIAN_MANAGE", "HR_STAFF_MANAGE", "MEMBERSHIP_MANAGE" })
	public PersonResponse update(@PathVariable Long id, @Valid @RequestBody UpdatePersonRequest request) {
		Person person = findOrThrow(id);
		if (request.firstName() != null) {
			person.setFirstName(requireNonBlank(request.firstName(), "firstName"));
		}
		if (request.lastName() != null) {
			person.setLastName(requireNonBlank(request.lastName(), "lastName"));
		}
		if (request.dateOfBirth() != null) {
			person.setDateOfBirth(request.dateOfBirth());
		}
		if (request.gender() != null) {
			person.setGender(blankToNull(Genders.validate(request.gender())));
		}
		if (request.phone() != null) {
			person.setPhone(blankToNull(request.phone()));
		}
		if (request.email() != null) {
			person.setEmail(blankToNull(request.email()));
		}
		if (request.address() != null) {
			person.setAddress(blankToNull(request.address()));
		}
		return PersonResponse.from(personRepository.save(person));
	}

	private static String requireNonBlank(String value, String field) {
		if (value.isBlank()) {
			throw new InvalidRequestException(field + " must not be blank");
		}
		return value;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private Person findOrThrow(Long id) {
		return personRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No person with id " + id));
	}
}
