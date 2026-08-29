package com.operion.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

/** Proves the Svix HMAC scheme Resend actually uses - signs, then verifies against the
 * real algorithm rather than a simplified stand-in, so a broken implementation here
 * would also fail against Resend's real webhooks. */
class ResendWebhookVerifierTest {

	private static final String SECRET = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";

	private final ResendWebhookVerifier verifier = new ResendWebhookVerifier();

	private String sign(String secret, String id, String timestamp, String body) throws Exception {
		byte[] keyBytes = Base64.getDecoder().decode(secret.substring("whsec_".length()));
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
		byte[] computed = mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
		return "v1," + Base64.getEncoder().encodeToString(computed);
	}

	@Test
	void acceptsACorrectlySignedPayload() throws Exception {
		String id = "msg_123";
		String timestamp = String.valueOf(Instant.now().getEpochSecond());
		String body = "{\"type\":\"email.delivered\"}";
		String signature = sign(SECRET, id, timestamp, body);

		assertThat(verifier.verify(body, id, timestamp, signature, SECRET)).isTrue();
	}

	@Test
	void rejectsATamperedBody() throws Exception {
		String id = "msg_123";
		String timestamp = String.valueOf(Instant.now().getEpochSecond());
		String signature = sign(SECRET, id, timestamp, "{\"type\":\"email.delivered\"}");

		assertThat(verifier.verify("{\"type\":\"email.bounced\"}", id, timestamp, signature, SECRET)).isFalse();
	}

	@Test
	void rejectsAWrongSecret() throws Exception {
		String id = "msg_123";
		String timestamp = String.valueOf(Instant.now().getEpochSecond());
		String body = "{\"type\":\"email.delivered\"}";
		String signature = sign(SECRET, id, timestamp, body);

		assertThat(verifier.verify(body, id, timestamp, signature, "whsec_" + Base64.getEncoder().encodeToString("different".getBytes())))
				.isFalse();
	}

	@Test
	void rejectsAStaleTimestamp() throws Exception {
		String id = "msg_123";
		String timestamp = String.valueOf(Instant.now().minusSeconds(3600).getEpochSecond());
		String body = "{\"type\":\"email.delivered\"}";
		String signature = sign(SECRET, id, timestamp, body);

		assertThat(verifier.verify(body, id, timestamp, signature, SECRET)).isFalse();
	}

	@Test
	void rejectsWhenNoSecretIsConfigured() throws Exception {
		String id = "msg_123";
		String timestamp = String.valueOf(Instant.now().getEpochSecond());
		String body = "{\"type\":\"email.delivered\"}";
		String signature = sign(SECRET, id, timestamp, body);

		assertThat(verifier.verify(body, id, timestamp, signature, "")).isFalse();
	}
}
