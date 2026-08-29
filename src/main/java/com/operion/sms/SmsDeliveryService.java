package com.operion.sms;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The seam a caller sending a real SMS goes through instead of talking to a specific
 * provider directly - same "try each configured sender in order, first success wins"
 * shape as com.operion.email.EmailDeliveryService. Takes {@code List<SmsSender>} for the
 * same testability/extensibility reasons documented there.
 */
@Service
public class SmsDeliveryService {

	private static final Logger log = LoggerFactory.getLogger(SmsDeliveryService.class);

	private final List<SmsSender> senders;

	public SmsDeliveryService(List<SmsSender> senders) {
		this.senders = senders;
	}

	/** Tries each configured sender in turn; returns the provider name and message id for
	 * the one that accepted it, or empty if every sender failed or none are configured.
	 * Never throws - the caller (NotificationDispatchService) records the outcome on its
	 * own row rather than this service owning any delivery-status bookkeeping itself. */
	public Optional<SendResult> trySend(String to, String body) {
		SmsMessage message = new SmsMessage(to, body);
		for (SmsSender sender : senders) {
			try {
				String messageId = sender.send(message);
				return Optional.of(new SendResult(sender.providerName(), messageId));
			} catch (SmsSendException ex) {
				log.warn("SMS delivery via {} failed for {}: {}", sender.providerName(), to, ex.getMessage());
			}
		}
		return Optional.empty();
	}

	public record SendResult(String provider, String messageId) {
	}
}
