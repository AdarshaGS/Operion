package com.operion.messaging.ws;

import java.security.Principal;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.auth.InvalidTokenException;
import com.operion.identity.auth.JwtService;
import com.operion.messaging.MessagingService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP has no HTTP Authorization header to lean on the way every REST endpoint does (a
 * browser's native WebSocket upgrade can't carry custom headers, and the SockJS
 * fallbacks share the same client API either way) - so auth happens per-frame instead:
 * CONNECT must carry a "Authorization: Bearer &lt;token&gt;" STOMP header (not an HTTP
 * one - @stomp/stompjs sends this as connectHeaders), decoded exactly like
 * JwtAuthenticationInterceptor does for REST. The resolved organisationId/personId are
 * stashed in the STOMP session's own attributes (one WebSocket connection, many frames
 * over its lifetime) so SUBSCRIBE can re-check thread membership per destination without
 * re-decoding the token - a client is otherwise free to attempt subscribing to any
 * /topic/threads/{id}, so that check is the only thing standing between one org's
 * messages and another's, or one person's DMs and someone else's.
 */
@Component
class StompAuthenticationInterceptor implements ChannelInterceptor {

	private static final String ORGANISATION_ID_ATTRIBUTE = "operion.organisationId";
	private static final String PERSON_ID_ATTRIBUTE = "operion.personId";

	private final JwtService jwtService;
	private final OrganisationMembershipRepository organisationMembershipRepository;
	private final MessagingService messagingService;

	StompAuthenticationInterceptor(JwtService jwtService, OrganisationMembershipRepository organisationMembershipRepository,
			MessagingService messagingService) {
		this.jwtService = jwtService;
		this.organisationMembershipRepository = organisationMembershipRepository;
		this.messagingService = messagingService;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}
		if (accessor.getCommand() == StompCommand.CONNECT) {
			authenticate(accessor);
		} else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
			authorizeSubscription(accessor);
		}
		return message;
	}

	private void authenticate(StompHeaderAccessor accessor) {
		String header = accessor.getFirstNativeHeader("Authorization");
		if (header == null || !header.startsWith("Bearer ")) {
			throw new InvalidTokenException("Missing bearer token on STOMP CONNECT", null);
		}
		JwtService.TokenPrincipal principal = jwtService.decodeToPrincipal(header.substring("Bearer ".length()));
		TenantContext.set(principal.organisationId(), principal.userId());
		try {
			Person person = organisationMembershipRepository.findByUserId(principal.userId()).stream()
					.findFirst()
					.map(OrganisationMembership::getPerson)
					.orElseThrow(() -> new InvalidTokenException("No organisation membership for this token's user", null));
			accessor.getSessionAttributes().put(ORGANISATION_ID_ATTRIBUTE, principal.organisationId());
			accessor.getSessionAttributes().put(PERSON_ID_ATTRIBUTE, person.getId());
			accessor.setUser((Principal) () -> String.valueOf(person.getId()));
		} finally {
			TenantContext.clear();
		}
	}

	private void authorizeSubscription(StompHeaderAccessor accessor) {
		Long organisationId = (Long) accessor.getSessionAttributes().get(ORGANISATION_ID_ATTRIBUTE);
		Long personId = (Long) accessor.getSessionAttributes().get(PERSON_ID_ATTRIBUTE);
		if (organisationId == null || personId == null) {
			throw new AuthorizationDeniedException("Not authenticated");
		}
		Long threadId = threadIdFromDestination(accessor.getDestination());
		if (threadId == null) {
			throw new AuthorizationDeniedException("Not a thread subscription");
		}
		TenantContext.set(organisationId, null);
		try {
			if (!messagingService.isParticipant(threadId, personId)) {
				throw new AuthorizationDeniedException("Not a participant of this thread");
			}
		} finally {
			TenantContext.clear();
		}
	}

	private Long threadIdFromDestination(String destination) {
		String prefix = "/topic/threads/";
		if (destination == null || !destination.startsWith(prefix)) {
			return null;
		}
		try {
			return Long.valueOf(destination.substring(prefix.length()));
		} catch (NumberFormatException ex) {
			return null;
		}
	}
}
