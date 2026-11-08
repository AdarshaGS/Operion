package com.operion.messaging.ws;

import java.security.Principal;
import java.util.Map;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.identity.UserStatus;
import com.operion.identity.auth.InvalidTokenException;
import com.operion.identity.auth.JwtService;
import com.operion.messaging.MessagingIdentityResolver;
import com.operion.messaging.ThreadParticipantRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
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
 * JwtAuthenticationInterceptor does for REST. The resolved identity is stashed in the STOMP
 * session's own attributes (one WebSocket connection, many frames over its lifetime) so
 * SUBSCRIBE can re-check thread membership per destination without re-decoding the token - a
 * client is otherwise free to attempt subscribing to any /topic/threads/{id}, so that check
 * is the only thing standing between one org's messages and another's, or one person's DMs
 * and someone else's.
 *
 * <p>Frames are an allow-list, not a block-list: only CONNECT (or its STOMP alias),
 * SUBSCRIBE, UNSUBSCRIBE, DISCONNECT and heartbeats from a client are accepted. In particular a
 * client SEND is rejected. This matters because this interceptor sits on the client INBOUND
 * channel, and Spring's simple broker is subscribed to that channel as well as to the broker
 * channel the server publishes on (SimpMessagingBroadcaster): an unrejected client "SEND
 * /topic/threads/5" is fanned out verbatim to every subscriber of thread 5, forging a message
 * with no sender, no persistence and no participant check. Server-originated messages never pass
 * through here (SimpMessagingTemplate publishes on the broker channel), so rejecting client SEND
 * does not affect delivery. There are no @MessageMapping handlers under the "/app" prefix, so
 * nothing legitimate needs client SEND.
 *
 * <p>CONNECT performs the same checks JwtAuthenticationInterceptor applies to every REST call:
 * the user exists and is ACTIVE, the token's credential version is current (a password change
 * kills older tokens), the organisation is open for sign-in, and the user holds an ACTIVE
 * membership in the token's organisation (whose Person is chosen deterministically by
 * MessagingIdentityResolver). SUBSCRIBE re-runs the same checks, so a user disabled, an org
 * suspended or a membership revoked after CONNECT cannot open new subscriptions. Residual
 * window: a subscription that already exists keeps receiving broadcasts until the socket closes,
 * because the simple broker fans messages out with no per-message hook, and a socket is not
 * closed when its access token expires. Both are bounded by the client reconnecting (which must
 * pass CONNECT again); closing live sockets on disable/suspend would need a server-side session
 * registry, which does not exist.
 */
@Component
class StompAuthenticationInterceptor implements ChannelInterceptor {

	private static final Logger log = LoggerFactory.getLogger(StompAuthenticationInterceptor.class);

	static final String ORGANISATION_ID_ATTRIBUTE = "operion.organisationId";
	static final String PERSON_ID_ATTRIBUTE = "operion.personId";
	static final String USER_ID_ATTRIBUTE = "operion.userId";
	static final String CREDENTIAL_VERSION_ATTRIBUTE = "operion.credentialVersion";

	private final JwtService jwtService;
	private final UserRepository userRepository;
	private final OrganisationRepository organisationRepository;
	private final MessagingIdentityResolver identityResolver;
	private final ThreadParticipantRepository threadParticipantRepository;

	StompAuthenticationInterceptor(JwtService jwtService, UserRepository userRepository, OrganisationRepository organisationRepository,
			MessagingIdentityResolver identityResolver, ThreadParticipantRepository threadParticipantRepository) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
		this.organisationRepository = organisationRepository;
		this.identityResolver = identityResolver;
		this.threadParticipantRepository = threadParticipantRepository;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		StompCommand command = accessor == null ? null : accessor.getCommand();
		if (command == null) {
			// A STOMP heartbeat is the only legitimate frame with no command.
			if (SimpMessageType.HEARTBEAT == SimpMessageHeaderAccessor.getMessageType(message.getHeaders())) {
				return message;
			}
			throw new AuthorizationDeniedException("Unsupported STOMP frame");
		}
		switch (command) {
			case CONNECT, STOMP -> authenticate(accessor);
			case SUBSCRIBE -> authorizeSubscription(accessor);
			case UNSUBSCRIBE, DISCONNECT -> {
				// Nothing to authorize: they only ever act on the sender's own session.
			}
			default -> {
				log.warn("Rejected client STOMP {} frame to {}", command, accessor.getDestination());
				throw new AuthorizationDeniedException("Clients may not send " + command + " frames");
			}
		}
		return message;
	}

	private void authenticate(StompHeaderAccessor accessor) {
		Map<String, Object> session = accessor.getSessionAttributes();
		if (session == null) {
			throw new InvalidTokenException("No STOMP session to authenticate", null);
		}
		String header = accessor.getFirstNativeHeader("Authorization");
		if (header == null || !header.startsWith("Bearer ")) {
			throw new InvalidTokenException("Missing bearer token on STOMP CONNECT", null);
		}
		JwtService.TokenPrincipal principal = jwtService.decodeToPrincipal(header.substring("Bearer ".length()));
		TenantContext.set(principal.organisationId(), principal.userId());
		try {
			Person person = requireValidSession(principal.userId(), principal.organisationId(), principal.credentialVersion());
			session.put(ORGANISATION_ID_ATTRIBUTE, principal.organisationId());
			session.put(PERSON_ID_ATTRIBUTE, person.getId());
			session.put(USER_ID_ATTRIBUTE, principal.userId());
			session.put(CREDENTIAL_VERSION_ATTRIBUTE, principal.credentialVersion());
			accessor.setUser((Principal) () -> String.valueOf(person.getId()));
		} finally {
			TenantContext.clear();
		}
	}

	private void authorizeSubscription(StompHeaderAccessor accessor) {
		Map<String, Object> session = accessor.getSessionAttributes();
		Long organisationId = session == null ? null : (Long) session.get(ORGANISATION_ID_ATTRIBUTE);
		Long personId = session == null ? null : (Long) session.get(PERSON_ID_ATTRIBUTE);
		Long userId = session == null ? null : (Long) session.get(USER_ID_ATTRIBUTE);
		Long credentialVersion = session == null ? null : (Long) session.get(CREDENTIAL_VERSION_ATTRIBUTE);
		if (organisationId == null || personId == null || userId == null || credentialVersion == null) {
			throw new AuthorizationDeniedException("Not authenticated");
		}
		Long threadId = threadIdFromDestination(accessor.getDestination());
		if (threadId == null) {
			throw new AuthorizationDeniedException("Not a thread subscription");
		}
		TenantContext.set(organisationId, userId);
		try {
			Person person = requireValidSession(userId, organisationId, credentialVersion);
			if (!person.getId().equals(personId)) {
				throw new AuthorizationDeniedException("Session identity changed - reconnect");
			}
			if (!threadParticipantRepository.existsByThreadIdAndPersonId(threadId, personId)) {
				throw new AuthorizationDeniedException("Not a participant of this thread");
			}
		} finally {
			TenantContext.clear();
		}
	}

	/** Mirrors JwtAuthenticationInterceptor.preHandle's per-request account gates (TenantContext already set). */
	private Person requireValidSession(Long userId, Long organisationId, long credentialVersion) {
		User user = userRepository.findById(userId).orElse(null);
		if (user == null || user.getStatus() != UserStatus.ACTIVE) {
			throw new InvalidTokenException("User is no longer active", null);
		}
		if (credentialVersion != user.getCredentialVersion()) {
			throw new InvalidTokenException("Token was issued before the user's last password change", null);
		}
		boolean organisationOpen = organisationRepository.findById(organisationId).map(Organisation::isOpenForSignIn).orElse(false);
		if (!organisationOpen) {
			throw new InvalidTokenException("Organisation is suspended or archived", null);
		}
		return identityResolver.findPerson(userId)
				.orElseThrow(() -> new InvalidTokenException("No active organisation membership for this token's user", null));
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
