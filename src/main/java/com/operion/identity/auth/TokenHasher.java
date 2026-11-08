package com.operion.identity.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 hex digest, used to store and look up high-entropy one-time tokens (password-reset and
 * email-verification links) without keeping the raw value. Unlike bcrypt it is deterministic, so
 * a token can be found by an indexed equality lookup instead of bcrypt-comparing every live row.
 * That is safe only because these tokens are 256-bit random values: there is nothing to brute
 * force, so a slow hash adds cost without adding protection. Do not use it for passwords.
 */
final class TokenHasher {

	private TokenHasher() {
	}

	static String sha256Hex(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by the JVM spec", ex);
		}
	}
}
