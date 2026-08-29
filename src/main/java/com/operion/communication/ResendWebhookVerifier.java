package com.operion.communication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Verifies Resend's webhook signature - Resend delivers webhooks via Svix, whose scheme
 * is HMAC-SHA256 over "{svix-id}.{svix-timestamp}.{rawBody}", base64-encoded, checked
 * against one or more "v1,&lt;sig&gt;" tokens in the svix-signature header. The secret
 * Resend/Svix hand out is prefixed "whsec_" over a base64 key - same
 * fail-closed-until-configured, constant-time-compare shape as
 * RazorpayHttpGateway.verifyWebhookSignature, adapted to Svix's specific encoding instead
 * of Razorpay's raw-hex HMAC.
 */
@Component
class ResendWebhookVerifier {

	private static final String SECRET_PREFIX = "whsec_";
	private static final long TIMESTAMP_TOLERANCE_SECONDS = 300;

	boolean verify(String rawBody, String svixId, String svixTimestamp, String svixSignatureHeader, String webhookSecret) {
		if (webhookSecret == null || webhookSecret.isBlank() || svixId == null || svixTimestamp == null || svixSignatureHeader == null) {
			return false;
		}
		if (!isTimestampFresh(svixTimestamp)) {
			return false;
		}
		try {
			String secretKey = webhookSecret.startsWith(SECRET_PREFIX) ? webhookSecret.substring(SECRET_PREFIX.length()) : webhookSecret;
			byte[] keyBytes = Base64.getDecoder().decode(secretKey);

			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
			String signedContent = svixId + "." + svixTimestamp + "." + rawBody;
			byte[] computed = mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8));
			String expected = Base64.getEncoder().encodeToString(computed);

			// The header can carry multiple space-separated "v1,<sig>" tokens (key
			// rotation) - a match on any one is valid.
			for (String token : svixSignatureHeader.split(" ")) {
				String signature = token.startsWith("v1,") ? token.substring("v1,".length()) : token;
				if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
					return true;
				}
			}
			return false;
		} catch (Exception ex) {
			return false;
		}
	}

	private boolean isTimestampFresh(String svixTimestamp) {
		try {
			long timestampSeconds = Long.parseLong(svixTimestamp);
			long ageSeconds = Math.abs(Instant.now().getEpochSecond() - timestampSeconds);
			return ageSeconds <= TIMESTAMP_TOLERANCE_SECONDS;
		} catch (NumberFormatException ex) {
			return false;
		}
	}
}
