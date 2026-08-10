package com.operion.communication.api;

import java.time.Instant;

import com.operion.communication.NotificationRecipient;

public record NotificationRecipientResponse(Long id, Long announcementId, String channel, String deliveryStatus, Instant sentAt, Instant readAt) {

	static NotificationRecipientResponse from(NotificationRecipient recipient) {
		return new NotificationRecipientResponse(recipient.getId(),
				recipient.getAnnouncement() == null ? null : recipient.getAnnouncement().getId(),
				recipient.getChannel().name(), recipient.getDeliveryStatus().name(), recipient.getSentAt(), recipient.getReadAt());
	}
}
