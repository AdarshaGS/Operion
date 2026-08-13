package com.operion.finance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The app's first outbound HTTP integration - uses a Spring-Boot-autoconfigured
 * RestClient.Builder rather than the static RestClient.create() factory, so the
 * correctly-wired Jackson 3.x (tools.jackson) message converter is used instead of
 * whatever RestClient's own default detection would find - see build.gradle's note on
 * this project's Jackson 3 rebrand already having bitten JJWT once.
 */
@Component
class RazorpayHttpGateway implements RazorpayGateway {

	private final RestClient restClient;

	RazorpayHttpGateway(RestClient.Builder restClientBuilder) {
		this.restClient = restClientBuilder.baseUrl("https://api.razorpay.com/v1").build();
	}

	@Override
	public String createOrder(RazorpayCredentials credentials, long amountInPaise, String currency, String receipt) {
		try {
			Map<String, Object> response = restClient.post()
					.uri("/orders")
					.headers(headers -> headers.setBasicAuth(credentials.keyId(), credentials.keySecret()))
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("amount", amountInPaise, "currency", currency, "receipt", receipt))
					.retrieve()
					.body(new ParameterizedTypeReference<Map<String, Object>>() {
					});
			return (String) response.get("id");
		} catch (RestClientException ex) {
			// Never leak this as a raw 500 with no body - same "no unhandled exception
			// reaches the client" rule this codebase already applies to
			// DataIntegrityViolationException in ApiExceptionHandler. Message deliberately
			// doesn't include ex's details (which may echo back credentials/request data);
			// the real cause is only in the server log via the wrapped cause.
			throw new PaymentGatewayException("Unable to reach the payment gateway", ex);
		}
	}

	@Override
	public boolean verifyWebhookSignature(String rawBody, String signatureHeader, String webhookSecret) {
		if (signatureHeader == null) {
			return false;
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] computedHash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
			String computedHex = HexFormat.of().formatHex(computedHash);
			// Constant-time comparison - a naive String.equals() leaks timing information
			// an attacker could use to forge a valid signature byte by byte.
			return MessageDigest.isEqual(computedHex.getBytes(StandardCharsets.UTF_8), signatureHeader.getBytes(StandardCharsets.UTF_8));
		} catch (Exception ex) {
			return false;
		}
	}
}
