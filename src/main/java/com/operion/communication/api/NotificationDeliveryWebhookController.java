package com.operion.communication.api;

import com.operion.communication.NotificationDeliveryWebhookService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Public, unauthenticated, and trusted only after NotificationDeliveryWebhookService
 * verifies the signature/secret for whichever provider called - same shape as
 * RazorpayWebhookController, including rawBody bound as a plain String so the exact
 * bytes the provider signed are what get hashed for verification.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class NotificationDeliveryWebhookController {

	private final NotificationDeliveryWebhookService webhookService;
	private final ObjectMapper objectMapper;

	public NotificationDeliveryWebhookController(NotificationDeliveryWebhookService webhookService, ObjectMapper objectMapper) {
		this.webhookService = webhookService;
		this.objectMapper = objectMapper;
	}

	/** https://resend.com/docs/dashboard/webhooks/event-types - Svix-signed; see
	 * ResendWebhookVerifier. */
	@PostMapping("/resend/email")
	public void receiveResendEmailEvent(@RequestBody String rawBody, @RequestHeader("svix-id") String svixId,
			@RequestHeader("svix-timestamp") String svixTimestamp, @RequestHeader("svix-signature") String svixSignature) {
		JsonNode root = objectMapper.readTree(rawBody);
		String eventType = root.path("type").asString(null);
		String messageId = root.path("data").path("email_id").asString(null);
		webhookService.handleResendEvent(rawBody, svixId, svixTimestamp, svixSignature, eventType, messageId);
	}

	/** https://developers.brevo.com/docs/transactional-webhooks - Brevo has no built-in
	 * signature; app.email.brevo.webhook-secret is checked against whatever header name
	 * the webhook is configured to send in Brevo's own dashboard (X-Webhook-Secret here). */
	@PostMapping("/brevo/email")
	public void receiveBrevoEmailEvent(@RequestBody String rawBody, @RequestHeader(value = "X-Webhook-Secret", required = false) String secret) {
		JsonNode root = objectMapper.readTree(rawBody);
		String eventType = root.path("event").asString(null);
		String messageId = root.path("message-id").asString(null);
		webhookService.handleBrevoEmailEvent(secret, eventType, messageId);
	}

	/** https://developers.brevo.com/docs/sms-webhooks - same no-built-in-signature shape as Brevo email. */
	@PostMapping("/brevo/sms")
	public void receiveBrevoSmsEvent(@RequestBody String rawBody, @RequestHeader(value = "X-Webhook-Secret", required = false) String secret) {
		JsonNode root = objectMapper.readTree(rawBody);
		String eventType = root.path("event").asString(null);
		String messageId = root.path("id").asString(null);
		webhookService.handleBrevoSmsEvent(secret, eventType, messageId);
	}
}
