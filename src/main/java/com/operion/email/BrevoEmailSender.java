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
 * https://developers.brevo.com/reference/sendtransacemail - same RestClient.Builder-based
 * HTTP integration shape as com.operion.finance.RazorpayHttpGateway. Blank config by
 * default (app.email.brevo.*) - send() fails cleanly with EmailSendException, same "boots
 * fine regardless, calls fail until real keys are set" convention as Razorpay. @Order(1)
 * makes this the first leg of EmailDeliveryService's List&lt;EmailSender&gt; fallback chain.
 */
@Component
@Order(1)
class BrevoEmailSender implements EmailSender {

	private final RestClient restClient;
	private final String apiKey;
	private final String senderEmail;
	private final String senderName;

	BrevoEmailSender(RestClient.Builder restClientBuilder, @Value("${app.email.brevo.api-key:}") String apiKey,
			@Value("${app.email.brevo.sender-email:}") String senderEmail,
			@Value("${app.email.brevo.sender-name:Operion}") String senderName) {
		this.restClient = restClientBuilder.baseUrl("https://api.brevo.com/v3").build();
		this.apiKey = apiKey;
		this.senderEmail = senderEmail;
		this.senderName = senderName;
	}

	@Override
	public void send(EmailMessage message) {
		if (apiKey.isBlank() || senderEmail.isBlank()) {
			throw new EmailSendException("Brevo is not configured (app.email.brevo.api-key / sender-email)");
		}
		try {
			restClient.post()
					.uri("/smtp/email")
					.header("api-key", apiKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of(
							"sender", Map.of("email", senderEmail, "name", senderName),
							"to", List.of(Map.of("email", message.to())),
							"subject", message.subject(),
							"htmlContent", message.htmlBody()))
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientException ex) {
			throw new EmailSendException("Brevo request failed", ex);
		}
	}

	@Override
	public String providerName() {
		return "brevo";
	}
}
