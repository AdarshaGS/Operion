package com.operion.communication;

/** EMAIL dispatches via com.operion.email.EmailDeliveryService, SMS via
 * com.operion.sms.SmsDeliveryService, WHATSAPP via com.operion.whatsapp.WhatsAppDeliveryService
 * - all three through NotificationDispatchService, only for a person who actually has
 * that contact field on file (see CommunicationService.channelIsUsable). */
public enum NotificationChannel {
	IN_APP,
	EMAIL,
	SMS,
	WHATSAPP
}
