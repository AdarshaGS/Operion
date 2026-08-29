package com.operion.messaging.api;

import java.time.Instant;

import com.operion.messaging.Message;

public record MessageResponse(Long id, Long threadId, Long senderPersonId, String senderName, String body, Instant sentAt) {

	public static MessageResponse from(Message message) {
		String senderName = message.getSender().getFirstName()
				+ (message.getSender().getLastName() == null ? "" : " " + message.getSender().getLastName());
		return new MessageResponse(message.getId(), message.getThread().getId(), message.getSender().getId(), senderName,
				message.getBody(), message.getCreatedAt());
	}
}
