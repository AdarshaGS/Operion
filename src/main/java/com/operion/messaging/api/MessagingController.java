package com.operion.messaging.api;

import java.util.List;

import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.messaging.Message;
import com.operion.messaging.MessageThread;
import com.operion.messaging.MessageThreadRepository;
import com.operion.messaging.MessagingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No @RequirePermission anywhere here (see RequirePermission's own javadoc on that
 * default) - deliberately: thread membership (ThreadParticipant, enforced inside
 * MessagingService) is the authorization model for two-way messaging, not a role-based
 * permission grant, same reasoning as MessagingService's own class doc.
 */
@RestController
@RequestMapping("/api/v1/messaging")
public class MessagingController {

	private final MessagingService messagingService;
	private final MessageThreadRepository messageThreadRepository;
	private final SectionRepository sectionRepository;
	private final PersonRepository personRepository;
	private final OrganisationMembershipRepository organisationMembershipRepository;

	public MessagingController(MessagingService messagingService, MessageThreadRepository messageThreadRepository,
			SectionRepository sectionRepository, PersonRepository personRepository,
			OrganisationMembershipRepository organisationMembershipRepository) {
		this.messagingService = messagingService;
		this.messageThreadRepository = messageThreadRepository;
		this.sectionRepository = sectionRepository;
		this.personRepository = personRepository;
		this.organisationMembershipRepository = organisationMembershipRepository;
	}

	@GetMapping("/threads")
	public List<MessageThreadResponse> listThreads() {
		return messagingService.listThreadsForPerson(currentPerson()).stream()
				.map(summary -> MessageThreadResponse.from(summary, messagingService.listParticipants(summary.thread())))
				.toList();
	}

	@PostMapping("/threads/class-group/{sectionId}")
	public MessageThreadResponse openClassGroupThread(@PathVariable Long sectionId) {
		Section section = sectionRepository.findById(sectionId)
				.orElseThrow(() -> new IllegalArgumentException("No section with id " + sectionId));
		MessageThread thread = messagingService.getOrCreateClassGroupThread(section);
		return threadResponse(thread);
	}

	@PostMapping("/threads/direct/{personId}")
	public MessageThreadResponse openDirectThread(@PathVariable Long personId) {
		Person other = personRepository.findById(personId).orElseThrow(() -> new IllegalArgumentException("No person with id " + personId));
		MessageThread thread = messagingService.getOrCreateDirectThread(currentPerson(), other);
		return threadResponse(thread);
	}

	@GetMapping("/threads/{threadId}/messages")
	public List<MessageResponse> listMessages(@PathVariable Long threadId) {
		List<Message> messages = messagingService.listMessages(findThread(threadId), currentPerson());
		return messages.stream().map(MessageResponse::from).toList();
	}

	@PostMapping("/threads/{threadId}/messages")
	public MessageResponse sendMessage(@PathVariable Long threadId, @RequestBody SendMessageRequest request) {
		Message message = messagingService.sendMessage(findThread(threadId), currentPerson(), request.body());
		return MessageResponse.from(message);
	}

	@PostMapping("/threads/{threadId}/read")
	public void markRead(@PathVariable Long threadId) {
		messagingService.markRead(findThread(threadId), currentPerson());
	}

	private MessageThreadResponse threadResponse(MessageThread thread) {
		boolean unread = messagingService.isUnreadFor(thread, currentPerson());
		return MessageThreadResponse.from(new MessagingService.ThreadSummary(thread, unread), messagingService.listParticipants(thread));
	}

	private MessageThread findThread(Long threadId) {
		return messageThreadRepository.findById(threadId).orElseThrow(() -> new IllegalArgumentException("No thread with id " + threadId));
	}

	/** A user has one Person per org even across multiple role memberships - same pattern as NotificationController.currentPerson. */
	private Person currentPerson() {
		return organisationMembershipRepository.findByUserId(TenantContext.getActorId()).stream()
				.findFirst()
				.map(OrganisationMembership::getPerson)
				.orElseThrow(() -> new IllegalStateException("No organisation membership for the current user"));
	}
}
