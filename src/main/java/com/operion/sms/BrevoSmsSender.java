package com.operion.sms;

import java.util.Map;

import com.operion.integration.ExternalServiceCredentialResolver;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * https://developers.brevo.com/reference/sendtransacsms - same RestClient.Builder-based
 * HTTP integration shape as com.operion.email.BrevoEmailSender (same provider account,
 * separate transactional-SMS product under it). Credentials come from
 * ExternalServiceCredentialResolver (service key "brevo") rather than @Value, resolved
 * fresh each call so a platform-admin edit takes effect on the next send - see
 * BrevoEmailSender's doc comment for the full rationale. send() fails cleanly with
 * SmsSendException when unconfigured, same "boots fine regardless, calls fail until real
 * keys are set" convention as email. @Order(1) makes this the first (currently only) leg
 * of SmsDeliveryService's List&lt;SmsSender&gt; chain.
 */
@Component
@Order(1)
class BrevoSmsSender implements SmsSender {

	private static final String SERVICE_KEY = "brevo";

	private final RestClient restClient;
	private final ExternalServiceCredentialResolver credentialResolver;

	BrevoSmsSender(RestClient.Builder restClientBuilder, ExternalServiceCredentialResolver credentialResolver) {
		this.restClient = restClientBuilder.baseUrl("https://api.brevo.com/v3").build();
		this.credentialResolver = credentialResolver;
	}

	@Override
	public String send(SmsMessage message) {
		String apiKey = credentialResolver.resolve(SERVICE_KEY, "sms.api-key")
				.orElseThrow(() -> new SmsSendException("Brevo SMS is not configured (external_service_properties: brevo/sms.api-key)"));
		String sender = credentialResolver.resolve(SERVICE_KEY, "sms.sender")
				.orElseThrow(() -> new SmsSendException("Brevo SMS is not configured (external_service_properties: brevo/sms.sender)"));
		try {
			// messageId here is what NotificationDeliveryWebhookService later matches
			// against the "id" field on Brevo's SMS delivery webhook payload - verify that
			// field name against Brevo's current docs before relying on it with real keys.
			Map<String, Object> response = restClient.post()
					.uri("/transactionalSMS/sms")
					.header("api-key", apiKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of(
							"sender", sender,
							"recipient", message.to(),
							"content", message.body(),
							"type", "transactional"))
					.retrieve()
					.body(new ParameterizedTypeReference<Map<String, Object>>() {
					});
			Object messageId = response == null ? null : response.get("messageId");
			return messageId == null ? null : String.valueOf(messageId);
		} catch (RestClientException ex) {
			throw new SmsSendException("Brevo SMS request failed", ex);
		}
	}

	@Override
	public String providerName() {
		return "brevo";
	}
}
