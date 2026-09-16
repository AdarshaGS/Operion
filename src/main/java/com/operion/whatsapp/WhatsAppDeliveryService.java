package com.operion.whatsapp;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The seam a caller sending a real WhatsApp message goes through instead of talking to a
 * specific provider directly - same "try each configured sender in order, first success
 * wins" shape as com.operion.sms.SmsDeliveryService. Takes {@code List<WhatsAppSender>}
 * for the same testability/extensibility reasons documented there.
 */
@Service
public class WhatsAppDeliveryService {

	private static final Logger log = LoggerFactory.getLogger(WhatsAppDeliveryService.class);

	private final List<WhatsAppSender> senders;

	public WhatsAppDeliveryService(List<WhatsAppSender> senders) {
		this.senders = senders;
	}

	/** Tries each configured sender in turn; returns the provider name and message id for
	 * the one that accepted it, or empty if every sender failed or none are configured.
	 * Never throws - the caller (NotificationDispatchService) records the outcome on its
	 * own row rather than this service owning any delivery-status bookkeeping itself. */
	public Optional<SendResult> trySend(String to, String body) {
		WhatsAppMessage message = new WhatsAppMessage(to, body);
		for (WhatsAppSender sender : senders) {
			try {
				String messageId = sender.send(message);
				return Optional.of(new SendResult(sender.providerName(), messageId));
			} catch (WhatsAppSendException ex) {
				log.warn("WhatsApp delivery via {} failed for {}: {}", sender.providerName(), to, ex.getMessage());
			}
		}
		return Optional.empty();
	}

	public record SendResult(String provider, String messageId) {
	}
}
