package com.operion.communication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.operion.common.TenantContext;
import com.operion.common.WebhookVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Handles the three provider delivery-confirmation callbacks - see
 * NotificationDeliveryWebhookController for the endpoints these back and
 * ResendWebhookVerifier for Resend's real Svix HMAC scheme. Brevo's transactional
 * email/SMS webhook product has no equivalent cryptographic signature; the real
 * mechanism it actually offers is a custom header value configured in Brevo's own
 * dashboard alongside the webhook URL, so that's what brevoEmailWebhookSecret/
 * brevoSmsWebhookSecret check here - fail-closed (rejects everything) until a real
 * secret is configured, same convention as every other blank-by-default provider config
 * in this codebase.
 *
 * <p>A "not found" cross-tenant lookup is an expected, silent no-op, not an error: Brevo's
 * email webhook fires for every email sent through that account, including ones sent via
 * EmailDeliveryService.sendBestEffort (staff invites, verification) that were never a
 * NotificationRecipient row to begin with.
 */
@Service
public class NotificationDeliveryWebhookService {

	private final NotificationRecipientRepository notificationRecipientRepository;
	private final ResendWebhookVerifier resendWebhookVerifier;
	private final String resendWebhookSecret;
	private final String brevoEmailWebhookSecret;
	private final String brevoSmsWebhookSecret;

	public NotificationDeliveryWebhookService(NotificationRecipientRepository notificationRecipientRepository,
			ResendWebhookVerifier resendWebhookVerifier, @Value("${app.email.resend.webhook-secret:}") String resendWebhookSecret,
			@Value("${app.email.brevo.webhook-secret:}") String brevoEmailWebhookSecret,
			@Value("${app.sms.brevo.webhook-secret:}") String brevoSmsWebhookSecret) {
		this.notificationRecipientRepository = notificationRecipientRepository;
		this.resendWebhookVerifier = resendWebhookVerifier;
		this.resendWebhookSecret = resendWebhookSecret;
		this.brevoEmailWebhookSecret = brevoEmailWebhookSecret;
		this.brevoSmsWebhookSecret = brevoSmsWebhookSecret;
	}

	public void handleResendEvent(String rawBody, String svixId, String svixTimestamp, String svixSignature, String eventType,
			String messageId) {
		if (!resendWebhookVerifier.verify(rawBody, svixId, svixTimestamp, svixSignature, resendWebhookSecret)) {
			throw new WebhookVerificationException("Invalid Resend webhook signature");
		}
		if ("email.delivered".equals(eventType)) {
			markDelivered("resend", messageId);
		}
	}

	public void handleBrevoEmailEvent(String secretHeader, String eventType, String messageId) {
		if (!constantTimeEquals(secretHeader, brevoEmailWebhookSecret)) {
			throw new WebhookVerificationException("Invalid Brevo email webhook secret");
		}
		if ("delivered".equals(eventType)) {
			markDelivered("brevo", messageId);
		}
	}

	public void handleBrevoSmsEvent(String secretHeader, String eventType, String messageId) {
		if (!constantTimeEquals(secretHeader, brevoSmsWebhookSecret)) {
			throw new WebhookVerificationException("Invalid Brevo SMS webhook secret");
		}
		if ("delivered".equals(eventType)) {
			markDelivered("brevo", messageId);
		}
	}

	// Deliberately not @Transactional: the cross-tenant lookup below must run (and its
	// session close) before TenantContext.set() takes effect, and the re-fetch+save after
	// it needs a session opened fresh under the now-set TenantContext - one outer
	// transaction spanning both would keep the first (no-tenant) session's resolved
	// identifier for the whole method, same reasoning as
	// FeePaymentGatewayService.handleWebhook.
	void markDelivered(String provider, String providerMessageId) {
		if (providerMessageId == null || providerMessageId.isBlank()) {
			return;
		}
		Long organisationId =
				notificationRecipientRepository.findOrganisationIdByProviderAndProviderMessageId(provider, providerMessageId).orElse(null);
		if (organisationId == null) {
			return;
		}

		TenantContext.set(organisationId, null);
		try {
			notificationRecipientRepository.findByProviderAndProviderMessageId(provider, providerMessageId).ifPresent(recipient -> {
				try {
					recipient.markDelivered();
					notificationRecipientRepository.save(recipient);
				} catch (IllegalStateException alreadyPastSent) {
					// A duplicate/late delivered event for a row that's already DELIVERED
					// (or moved on to READ) - providers can redeliver webhook events, this
					// is expected and not an error.
				}
			});
		} finally {
			TenantContext.clear();
		}
	}

	private boolean constantTimeEquals(String provided, String configured) {
		if (configured == null || configured.isBlank() || provided == null) {
			return false;
		}
		return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), configured.getBytes(StandardCharsets.UTF_8));
	}
}
