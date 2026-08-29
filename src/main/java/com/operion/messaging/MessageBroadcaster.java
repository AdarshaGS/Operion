package com.operion.messaging;

import com.operion.messaging.api.MessageResponse;

/**
 * The seam MessagingService pushes a newly-sent message through to live subscribers,
 * instead of talking to Spring's SimpMessagingTemplate directly - same "hand-written stub
 * over the real external dependency" testability pattern as com.operion.email.EmailSender
 * (a WebSocket broker connection is exactly the kind of thing a plain @DataJpaTest has
 * no context for). See com.operion.messaging.ws.SimpMessagingBroadcaster for the real one.
 */
public interface MessageBroadcaster {

	void broadcast(Long threadId, MessageResponse message);
}
