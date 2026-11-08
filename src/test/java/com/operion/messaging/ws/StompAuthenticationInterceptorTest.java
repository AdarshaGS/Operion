package com.operion.messaging.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * Unit tests for the STOMP channel interceptor (no Spring context): the frame allow-list (a client SEND to
 * /topic must never reach the broker), the CONNECT/SUBSCRIBE account gates that mirror
 * JwtAuthenticationInterceptor, deterministic identity selection, and a miniature of the real channel wiring
 * proving a rejected client SEND is not fanned out while a server-originated broadcast still is.
 */
class StompAuthenticationInterceptorTest {

	private static final Long USER_ID = 7L;
	private static final Long ORG_ID = 3L;
	private static final Long PERSON_ID = 70L;

	private final JwtService jwtService = mock(JwtService.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final OrganisationRepository organisationRepository = mock(OrganisationRepository.class);
	private final OrganisationMembershipRepository membershipRepository = mock(OrganisationMembershipRepository.class);
	private final ThreadParticipantRepository threadParticipantRepository = mock(ThreadParticipantRepository.class);

	private final User user = mock(User.class);
	private final Organisation organisation = mock(Organisation.class);

	private StompAuthenticationInterceptor interceptor;

	@BeforeEach
	void setUp() {
		interceptor = new StompAuthenticationInterceptor(jwtService, userRepository, organisationRepository,
				new MessagingIdentityResolver(membershipRepository), threadParticipantRepository);
		when(jwtService.decodeToPrincipal("good")).thenReturn(new JwtService.TokenPrincipal(USER_ID, ORG_ID, 0L));
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
		when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(user.getCredentialVersion()).thenReturn(0L);
		when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(organisation));
		when(organisation.isOpenForSignIn()).thenReturn(true);
		when(membershipRepository.findByUserId(USER_ID)).thenReturn(List.of(membership(500L, MembershipStatus.ACTIVE, PERSON_ID)));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private static OrganisationMembership membership(Long id, MembershipStatus status, Long personId) {
		OrganisationMembership membership = mock(OrganisationMembership.class);
		Person person = mock(Person.class);
		when(person.getId()).thenReturn(personId);
		when(membership.getId()).thenReturn(id);
		when(membership.getStatus()).thenReturn(status);
		when(membership.getPerson()).thenReturn(person);
		return membership;
	}

	private static Message<byte[]> frame(StompCommand command, String destination, String bearer, Map<String, Object> session) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		if (destination != null) {
			accessor.setDestination(destination);
		}
		if (bearer != null) {
			accessor.addNativeHeader("Authorization", "Bearer " + bearer);
		}
		accessor.setSessionAttributes(session);
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}

	private Map<String, Object> connected() {
		Map<String, Object> session = new HashMap<>();
		interceptor.preSend(frame(StompCommand.CONNECT, null, "good", session), null);
		return session;
	}

	@Test
	void validConnectRecordsTheSessionIdentityAndLeavesNoTenantBehind() {
		Map<String, Object> session = new HashMap<>();

		Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, null, "good", session), null);

		assertThat(result).isNotNull();
		assertThat(session).containsEntry(StompAuthenticationInterceptor.ORGANISATION_ID_ATTRIBUTE, ORG_ID)
				.containsEntry(StompAuthenticationInterceptor.PERSON_ID_ATTRIBUTE, PERSON_ID)
				.containsEntry(StompAuthenticationInterceptor.USER_ID_ATTRIBUTE, USER_ID);
		assertThat(TenantContext.getOrganisationId()).isNull();
	}

	@Test
	void theStompCommandAliasIsAuthenticatedLikeConnect() {
		Map<String, Object> session = new HashMap<>();
		interceptor.preSend(frame(StompCommand.STOMP, null, "good", session), null);
		assertThat(session).containsKey(StompAuthenticationInterceptor.PERSON_ID_ATTRIBUTE);

		Map<String, Object> rejected = new HashMap<>();
		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.STOMP, null, null, rejected), null))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void clientSendToATopicIsRejectedEvenWhenConnected() {
		Map<String, Object> session = connected();

		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/topic/threads/5", null, session), null))
				.isInstanceOf(AuthorizationDeniedException.class);
		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/anything", null, session), null))
				.isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void clientSendWithoutEverConnectingIsRejected() {
		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/topic/threads/5", null, new HashMap<>()), null))
				.isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void otherClientFramesAreRejected() {
		Map<String, Object> session = connected();
		for (StompCommand command : List.of(StompCommand.ACK, StompCommand.NACK, StompCommand.BEGIN, StompCommand.COMMIT,
				StompCommand.ABORT, StompCommand.ERROR, StompCommand.MESSAGE, StompCommand.RECEIPT, StompCommand.CONNECTED)) {
			assertThatThrownBy(() -> interceptor.preSend(frame(command, "/topic/threads/5", null, session), null))
					.as(command.name()).isInstanceOf(AuthorizationDeniedException.class);
		}
	}

	@Test
	void heartbeatsUnsubscribeAndDisconnectAreAllowed() {
		Map<String, Object> session = connected();
		StompHeaderAccessor heartbeat = StompHeaderAccessor.createForHeartbeat();
		Message<byte[]> heartbeatMessage = MessageBuilder.createMessage(new byte[0], heartbeat.getMessageHeaders());

		assertThat(interceptor.preSend(heartbeatMessage, null)).isNotNull();
		assertThat(interceptor.preSend(frame(StompCommand.UNSUBSCRIBE, null, null, session), null)).isNotNull();
		assertThat(interceptor.preSend(frame(StompCommand.DISCONNECT, null, null, session), null)).isNotNull();
	}

	@Test
	void aMessageThatIsNotAStompFrameIsRejected() {
		Message<String> notStomp = MessageBuilder.withPayload("x").build();

		assertThatThrownBy(() -> interceptor.preSend(notStomp, null)).isInstanceOf(AuthorizationDeniedException.class);
	}

	@Test
	void connectWithoutABearerTokenIsRejected() {
		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, new HashMap<>()), null))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void connectByASuspendedLockedOrDisabledUserIsRejected() {
		for (UserStatus status : List.of(UserStatus.LOCKED, UserStatus.DISABLED, UserStatus.PENDING)) {
			when(user.getStatus()).thenReturn(status);
			Map<String, Object> session = new HashMap<>();

			assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, "good", session), null))
					.as(status.name()).isInstanceOf(InvalidTokenException.class);
			assertThat(session).isEmpty();
			assertThat(TenantContext.getOrganisationId()).isNull();
		}
	}

	@Test
	void connectByAUserThatNoLongerExistsIsRejected() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, "good", new HashMap<>()), null))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void connectWithATokenIssuedBeforeAPasswordChangeIsRejected() {
		when(user.getCredentialVersion()).thenReturn(1L);

		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, "good", new HashMap<>()), null))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void connectIntoASuspendedOrArchivedOrganisationIsRejected() {
		when(organisation.isOpenForSignIn()).thenReturn(false);

		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, "good", new HashMap<>()), null))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void connectWithOnlyInactiveMembershipsIsRejected() {
		when(membershipRepository.findByUserId(USER_ID)).thenReturn(List.of(membership(500L, MembershipStatus.INACTIVE, PERSON_ID)));

		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, "good", new HashMap<>()), null))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void identityIsTheLowestIdActiveMembershipRegardlessOfRepositoryOrder() {
		when(membershipRepository.findByUserId(USER_ID)).thenReturn(List.of(
				membership(900L, MembershipStatus.ACTIVE, 90L),
				membership(100L, MembershipStatus.INACTIVE, 10L),
				membership(300L, MembershipStatus.ACTIVE, 30L)));
		Map<String, Object> session = new HashMap<>();

		interceptor.preSend(frame(StompCommand.CONNECT, null, "good", session), null);

		assertThat(session).containsEntry(StompAuthenticationInterceptor.PERSON_ID_ATTRIBUTE, 30L);
	}

	@Test
	void subscribeToAThreadTheUserIsNotAParticipantOfIsRejected() {
		Map<String, Object> session = connected();
		when(threadParticipantRepository.existsByThreadIdAndPersonId(5L, PERSON_ID)).thenReturn(false);

		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/threads/5", null, session), null))
				.isInstanceOf(AuthorizationDeniedException.class);
		assertThat(TenantContext.getOrganisationId()).isNull();
	}

	@Test
	void subscribeToAThreadTheUserIsAParticipantOfIsAllowed() {
		Map<String, Object> session = connected();
		when(threadParticipantRepository.existsByThreadIdAndPersonId(5L, PERSON_ID)).thenReturn(true);

		assertThat(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/threads/5", null, session), null)).isNotNull();
	}

	@Test
	void subscribeBeforeConnectingOrToAnotherDestinationIsRejected() {
		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/threads/5", null, new HashMap<>()), null))
				.isInstanceOf(AuthorizationDeniedException.class);

		Map<String, Object> session = connected();
		for (String destination : List.of("/topic/other", "/topic/threads/abc", "/queue/x", "/user/queue/x")) {
			assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, destination, null, session), null))
					.as(destination).isInstanceOf(AuthorizationDeniedException.class);
		}
	}

	@Test
	void subscribeAfterTheAccountWasDisabledSinceConnectIsRejected() {
		Map<String, Object> session = connected();
		when(threadParticipantRepository.existsByThreadIdAndPersonId(anyLong(), anyLong())).thenReturn(true);
		when(user.getStatus()).thenReturn(UserStatus.DISABLED);

		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/threads/5", null, session), null))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void subscribeAfterTheOrganisationWasSuspendedOrTheMembershipRevokedIsRejected() {
		Map<String, Object> session = connected();
		when(threadParticipantRepository.existsByThreadIdAndPersonId(anyLong(), anyLong())).thenReturn(true);

		when(organisation.isOpenForSignIn()).thenReturn(false);
		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/threads/5", null, session), null))
				.isInstanceOf(InvalidTokenException.class);

		when(organisation.isOpenForSignIn()).thenReturn(true);
		when(membershipRepository.findByUserId(USER_ID)).thenReturn(List.of(membership(500L, MembershipStatus.INACTIVE, PERSON_ID)));
		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/threads/5", null, session), null))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void aRejectedClientSendIsNeverFannedOutButAServerBroadcastIs() {
		// A miniature of WebSocketMessageBrokerConfigurationSupport's wiring: the simple broker consumes BOTH the
		// client inbound channel (where the interceptor sits) and the broker channel the server publishes on.
		ExecutorSubscribableChannel clientInbound = new ExecutorSubscribableChannel();
		ExecutorSubscribableChannel brokerChannel = new ExecutorSubscribableChannel();
		ExecutorSubscribableChannel clientOutbound = new ExecutorSubscribableChannel();
		List<Message<?>> delivered = new CopyOnWriteArrayList<>();
		clientOutbound.subscribe(delivered::add);
		clientInbound.addInterceptor(interceptor);
		SimpleBrokerMessageHandler broker = new SimpleBrokerMessageHandler(clientInbound, clientOutbound, brokerChannel, List.of("/topic"));
		clientInbound.subscribe(broker);
		brokerChannel.subscribe(broker);
		broker.start();

		Map<String, Object> session = connected();
		when(threadParticipantRepository.existsByThreadIdAndPersonId(5L, PERSON_ID)).thenReturn(true);
		StompHeaderAccessor subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		subscribe.setDestination("/topic/threads/5");
		subscribe.setSubscriptionId("sub-0");
		subscribe.setSessionId("session-1");
		subscribe.setSessionAttributes(session);
		subscribe.setLeaveMutable(true);
		clientInbound.send(MessageBuilder.createMessage(new byte[0], subscribe.getMessageHeaders()));

		StompHeaderAccessor forged = StompHeaderAccessor.create(StompCommand.SEND);
		forged.setDestination("/topic/threads/5");
		forged.setSessionId("session-1");
		forged.setSessionAttributes(session);
		forged.setLeaveMutable(true);
		assertThatThrownBy(() -> clientInbound.send(MessageBuilder.createMessage("forged".getBytes(), forged.getMessageHeaders())))
				.isInstanceOf(MessageDeliveryException.class);
		assertThat(delivered).isEmpty();

		new SimpMessagingTemplate(brokerChannel).convertAndSend("/topic/threads/5", "from the server");
		assertThat(delivered).hasSize(1);
		assertThat(MessageHeaderAccessor.getAccessor(delivered.get(0), StompHeaderAccessor.class)).isNotNull();
		assertThat(new String((byte[]) toBytes(delivered.get(0).getPayload()))).contains("from the server");
	}

	private static Object toBytes(Object payload) {
		return payload instanceof byte[] bytes ? bytes : String.valueOf(payload).getBytes();
	}
}
