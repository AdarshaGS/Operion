package com.operion.communication.api;

import java.util.List;

import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.common.TenantContext;
import com.operion.communication.CommunicationService;
import com.operion.communication.NotificationRecipient;
import com.operion.communication.NotificationRecipientRepository;
import com.operion.identity.Person;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

	private final CommunicationService communicationService;
	private final NotificationRecipientRepository notificationRecipientRepository;
	private final OrganisationMembershipRepository organisationMembershipRepository;

	public NotificationController(CommunicationService communicationService, NotificationRecipientRepository notificationRecipientRepository,
			OrganisationMembershipRepository organisationMembershipRepository) {
		this.communicationService = communicationService;
		this.notificationRecipientRepository = notificationRecipientRepository;
		this.organisationMembershipRepository = organisationMembershipRepository;
	}

	@GetMapping("/me")
	public List<NotificationRecipientResponse> myNotifications() {
		Long personId = currentPerson().getId();
		return notificationRecipientRepository.findByPersonIdOrderByCreatedAtDesc(personId).stream()
				.map(NotificationRecipientResponse::from).toList();
	}

	@PostMapping("/{id}/read")
	public NotificationRecipientResponse markRead(@PathVariable Long id) {
		NotificationRecipient recipient = notificationRecipientRepository.findByIdAndPersonId(id, currentPerson().getId())
				.orElseThrow(() -> new IllegalArgumentException("No notification with id " + id + " for the current user"));
		return NotificationRecipientResponse.from(communicationService.markRead(recipient));
	}

	/** A user has one Person per org even across multiple role memberships - see identity model in ai-context/load-context.md. */
	private Person currentPerson() {
		return organisationMembershipRepository.findByUserId(TenantContext.getActorId()).stream()
				.findFirst()
				.map(OrganisationMembership::getPerson)
				.orElseThrow(() -> new IllegalStateException("No organisation membership for the current user"));
	}
}
