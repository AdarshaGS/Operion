package com.operion.communication;

/**
 * IN_APP rows land on SENT immediately (row creation IS delivery for that channel) and
 * never reach DELIVERED - there's no external provider to confirm receipt. EMAIL/SMS
 * rows start PENDING, move to SENT once NotificationDispatchService gets the provider to
 * accept the send, then to DELIVERED once NotificationDeliveryWebhookService gets that
 * provider's own delivery-confirmation callback (distinct from SENT, which only means
 * "the gateway accepted it," not "the recipient's inbox/handset actually got it") - or to
 * FAILED at either step on a real provider error.
 */
public enum DeliveryStatus {
	PENDING,
	SENT,
	DELIVERED,
	FAILED,
	READ
}
