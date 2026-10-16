package com.operion.platform.crm;

import java.util.List;

import org.springframework.stereotype.Service;
import com.operion.common.persistence.PlatformTransactional;

@Service
public class ContactService {

	private final ContactRepository contactRepository;
	private final CrmDirectory crmDirectory;

	public ContactService(ContactRepository contactRepository, CrmDirectory crmDirectory) {
		this.contactRepository = contactRepository;
		this.crmDirectory = crmDirectory;
	}

	public List<Contact> list(Long leadId, Long organisationId) {
		CrmOwner owner = crmDirectory.owner(leadId, organisationId);
		return owner.leadId() != null ? contactRepository.findByLeadIdOrderByNameAsc(owner.leadId())
				: contactRepository.findByOrganisationIdOrderByNameAsc(owner.organisationId());
	}

	@PlatformTransactional
	public Contact create(Long leadId, Long organisationId, String name, String role, String email, String phone) {
		CrmOwner owner = crmDirectory.owner(leadId, organisationId);
		return contactRepository.save(new Contact(owner, name.trim(), LeadService.blankToNull(role),
				LeadService.blankToNull(email), LeadService.blankToNull(phone)));
	}

	/** Partial update: a null field leaves the current value alone; a blank string clears it. */
	@PlatformTransactional
	public Contact update(Long id, String name, String role, String email, String phone) {
		Contact contact = get(id);
		contact.update(name != null && !name.isBlank() ? name.trim() : contact.getName(),
				role != null ? LeadService.blankToNull(role) : contact.getRole(),
				email != null ? LeadService.blankToNull(email) : contact.getEmail(),
				phone != null ? LeadService.blankToNull(phone) : contact.getPhone());
		return contactRepository.save(contact);
	}

	@PlatformTransactional
	public void delete(Long id) {
		contactRepository.delete(get(id));
	}

	private Contact get(Long id) {
		return contactRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No contact with id " + id));
	}
}
