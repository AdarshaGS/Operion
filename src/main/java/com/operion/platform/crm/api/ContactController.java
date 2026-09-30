package com.operion.platform.crm.api;

import java.util.List;

import com.operion.platform.crm.ContactService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/crm/contacts")
public class ContactController {

	private final ContactService contactService;

	public ContactController(ContactService contactService) {
		this.contactService = contactService;
	}

	@GetMapping
	public List<ContactResponse> list(@RequestParam(required = false) Long leadId,
			@RequestParam(required = false) Long organisationId) {
		return contactService.list(leadId, organisationId).stream().map(ContactResponse::from).toList();
	}

	@PostMapping
	public ContactResponse create(@Valid @RequestBody CreateContactRequest request) {
		return ContactResponse.from(contactService.create(request.leadId(), request.organisationId(), request.name(),
				request.role(), request.email(), request.phone()));
	}

	@PatchMapping("/{id}")
	public ContactResponse update(@PathVariable Long id, @RequestBody UpdateContactRequest request) {
		return ContactResponse.from(contactService.update(id, request.name(), request.role(), request.email(), request.phone()));
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable Long id) {
		contactService.delete(id);
	}
}
