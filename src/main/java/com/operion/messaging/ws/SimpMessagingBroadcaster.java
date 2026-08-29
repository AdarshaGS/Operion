package com.operion.messaging.ws;

import com.operion.messaging.MessageBroadcaster;
import com.operion.messaging.api.MessageResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** The real MessageBroadcaster - pushes to every client subscribed to
 * /topic/threads/{threadId}, which StompAuthenticationInterceptor only let a participant
 * subscribe to in the first place. */
@Component
class SimpMessagingBroadcaster implements MessageBroadcaster {

	private final SimpMessagingTemplate simpMessagingTemplate;

	SimpMessagingBroadcaster(SimpMessagingTemplate simpMessagingTemplate) {
		this.simpMessagingTemplate = simpMessagingTemplate;
	}

	@Override
	public void broadcast(Long threadId, MessageResponse message) {
		simpMessagingTemplate.convertAndSend("/topic/threads/" + threadId, message);
	}
}
