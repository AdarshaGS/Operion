package com.operion.email;

import java.util.List;
import java.util.Map;

import com.operion.integration.ExternalServiceCredentialResolver;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * https://developers.brevo.com/reference/sendtransacemail - same RestClient.Builder-based
 * HTTP integration shape as com.operion.finance.RazorpayHttpGateway. Credentials come
 * from ExternalServiceCredentialResolver (service key "brevo") rather than @Value, so a
 * platform admin editing them from the Integrations screen takes effect on the next
 * send - resolved fresh each call, not cached at construction. send() fails cleanly with
 * EmailSendException when unconfigured, same "boots fine regardless, calls fail until
 * real keys are set" convention as Razorpay. @Order(1) makes this the first leg of
 * EmailDeliveryService's List&lt;EmailSender&gt; fallback chain.
 */
@Component
@Order(1)
class BrevoEmailSender implements EmailSender {

	private static final String SERVICE_KEY = "brevo";

	private final RestClient restClient;
	private final ExternalServiceCredentialResolver credentialResolver;

	BrevoEmailSender(RestClient.Builder restClientBuilder, ExternalServiceCredentialResolver credentialResolver) {
		this.restClient = restClientBuilder.baseUrl("https://api.brevo.com/v3").build();
		this.credentialResolver = credentialResolver;
	}

	@Override
	public String send(EmailMessage message) {
		String apiKey = credentialResolver.resolve(SERVICE_KEY, "email.api-key")
				.orElseThrow(() -> new EmailSendException("Brevo is not configured (external_service_properties: brevo/email.api-key)"));
		String senderEmail = credentialResolver.resolve(SERVICE_KEY, "email.sender-email")
				.orElseThrow(() -> new EmailSendException("Brevo is not configured (external_service_properties: brevo/email.sender-email)"));
		String senderName = credentialResolver.resolve(SERVICE_KEY, "email.sender-name").orElse("Operion");
		try {
			// messageId here is what NotificationDeliveryWebhookService later matches
			// against the "message-id" field on Brevo's delivery webhook payload - verify
			// that field name against Brevo's current docs before relying on it with real keys.
			Map<String, Object> response = restClient.post()
					.uri("/smtp/email")
					.header("api-key", apiKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of(
							"sender", Map.of("email", senderEmail, "name", senderName),
							"to", List.of(Map.of("email", message.to())),
							"subject", message.subject(),
							"htmlContent", message.htmlBody()))
					.retrieve()
					.body(new ParameterizedTypeReference<Map<String, Object>>() {
					});
			return response == null ? null : (String) response.get("messageId");
		} catch (RestClientException ex) {
			throw new EmailSendException("Brevo request failed", ex);
		}
	}

	@Override
	public String providerName() {
		return "brevo";
	}
}
