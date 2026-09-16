package com.operion.whatsapp;

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
 * https://developers.facebook.com/docs/whatsapp/cloud-api/reference/messages - Meta's
 * own WhatsApp Cloud API, not a 3rd-party BSP, so no separate "sender" identity property
 * is needed the way Brevo email/SMS have one: the phone number is implied by
 * {@code whatsapp.phone-number-id} on Meta's side. Same RestClient.Builder-based HTTP
 * integration shape as com.operion.sms.BrevoSmsSender; credentials come from
 * ExternalServiceCredentialResolver (service key "whatsapp"), resolved fresh each call so
 * a platform-admin/org-admin edit takes effect on the next send with no restart needed.
 * @Order(1) makes this the first (currently only) leg of WhatsAppDeliveryService's
 * List&lt;WhatsAppSender&gt; chain.
 */
@Component
@Order(1)
class WhatsAppBusinessApiSender implements WhatsAppSender {

	private static final String SERVICE_KEY = "whatsapp";

	private final RestClient restClient;
	private final ExternalServiceCredentialResolver credentialResolver;

	WhatsAppBusinessApiSender(RestClient.Builder restClientBuilder, ExternalServiceCredentialResolver credentialResolver) {
		this.restClient = restClientBuilder.baseUrl("https://graph.facebook.com/v20.0").build();
		this.credentialResolver = credentialResolver;
	}

	@Override
	public String send(WhatsAppMessage message) {
		String accessToken = credentialResolver.resolve(SERVICE_KEY, "whatsapp.api-key")
				.orElseThrow(() -> new WhatsAppSendException("WhatsApp is not configured (external_service_properties: whatsapp/whatsapp.api-key)"));
		String phoneNumberId = credentialResolver.resolve(SERVICE_KEY, "whatsapp.phone-number-id")
				.orElseThrow(() -> new WhatsAppSendException(
						"WhatsApp is not configured (external_service_properties: whatsapp/whatsapp.phone-number-id)"));
		try {
			Map<String, Object> response = restClient.post()
					.uri("/{phoneNumberId}/messages", phoneNumberId)
					.header("Authorization", "Bearer " + accessToken)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of(
							"messaging_product", "whatsapp",
							"to", message.to(),
							"type", "text",
							"text", Map.of("body", message.body())))
					.retrieve()
					.body(new ParameterizedTypeReference<Map<String, Object>>() {
					});
			Object messages = response == null ? null : response.get("messages");
			if (messages instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
				Object id = first.get("id");
				return id == null ? null : String.valueOf(id);
			}
			return null;
		} catch (RestClientException ex) {
			throw new WhatsAppSendException("WhatsApp request failed", ex);
		}
	}

	@Override
	public String providerName() {
		return "whatsapp";
	}
}
