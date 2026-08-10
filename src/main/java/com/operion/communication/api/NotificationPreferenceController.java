package com.operion.communication.api;

import java.util.List;

import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.common.TenantContext;
import com.operion.communication.CommunicationService;
import com.operion.communication.NotificationChannel;
import com.operion.communication.NotificationPreferenceRepository;
import com.operion.identity.Person;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification-preferences")
public class NotificationPreferenceController {

	private final CommunicationService communicationService;
	private final NotificationPreferenceRepository notificationPreferenceRepository;
	private final OrganisationMembershipRepository organisationMembershipRepository;

	public NotificationPreferenceController(CommunicationService communicationService,
			NotificationPreferenceRepository notificationPreferenceRepository,
			OrganisationMembershipRepository organisationMembershipRepository) {
		this.communicationService = communicationService;
		this.notificationPreferenceRepository = notificationPreferenceRepository;
		this.organisationMembershipRepository = organisationMembershipRepository;
	}

	@GetMapping("/me")
	public List<NotificationPreferenceResponse> myPreferences() {
		return notificationPreferenceRepository.findByPersonId(currentPerson().getId()).stream()
				.map(NotificationPreferenceResponse::from).toList();
	}

	@PutMapping("/me")
	public NotificationPreferenceResponse setMyPreference(@RequestBody SetNotificationPreferenceRequest request) {
		return NotificationPreferenceResponse.from(communicationService.setPreference(
				currentPerson(), NotificationChannel.valueOf(request.channel()), request.enabled()));
	}

	/** A user has one Person per org even across multiple role memberships - see identity model in ai-context/load-context.md. */
	private Person currentPerson() {
		return organisationMembershipRepository.findByUserId(TenantContext.getActorId()).stream()
				.findFirst()
				.map(OrganisationMembership::getPerson)
				.orElseThrow(() -> new IllegalStateException("No organisation membership for the current user"));
	}
}
