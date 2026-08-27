package com.operion.email;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * https://resend.com/docs/api-reference/emails/send-email - the fallback leg of
 * EmailDeliveryService's two-provider chain (@Order(2), tried after BrevoEmailSender),
 * same shape as BrevoEmailSender otherwise.
 */
@Component
@Order(2)
class ResendEmailSender implements EmailSender {

	private final RestClient restClient;
	private final String apiKey;
	private final String senderEmail;
	private final String senderName;

	ResendEmailSender(RestClient.Builder restClientBuilder, @Value("${app.email.resend.api-key:}") String apiKey,
			@Value("${app.email.resend.sender-email:}") String senderEmail,
			@Value("${app.email.resend.sender-name:Operion}") String senderName) {
		this.restClient = restClientBuilder.baseUrl("https://api.resend.com").build();
		this.apiKey = apiKey;
		this.senderEmail = senderEmail;
		this.senderName = senderName;
	}

	@Override
	public void send(EmailMessage message) {
		if (apiKey.isBlank() || senderEmail.isBlank()) {
			throw new EmailSendException("Resend is not configured (app.email.resend.api-key / sender-email)");
		}
		try {
			restClient.post()
					.uri("/emails")
					.headers(headers -> headers.setBearerAuth(apiKey))
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of(
							"from", senderName + " <" + senderEmail + ">",
							"to", List.of(message.to()),
							"subject", message.subject(),
							"html", message.htmlBody()))
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientException ex) {
			throw new EmailSendException("Resend request failed", ex);
		}
	}

	@Override
	public String providerName() {
		return "resend";
	}
}
