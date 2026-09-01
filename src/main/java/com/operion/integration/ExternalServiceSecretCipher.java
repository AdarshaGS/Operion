package com.operion.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * AES encryption for {@link ExternalServiceProperty#getPropertyValue()} when
 * {@link ExternalServiceProperty#isSecret()} is true - the only reversible-encryption use
 * in the app so far (SecurityConfig's BCryptPasswordEncoder is one-way, for login
 * passwords). Keyed by app.integration.secret-key, same "dev-only default baked in, MUST
 * be overridden in any real deployment" convention as app.jwt.secret /
 * app.jwt.platform-secret. The salt isn't itself a secret (it's a KDF parameter, not the
 * key) so a fixed constant is fine here - only the key needs to be kept safe.
 */
@Component
class ExternalServiceSecretCipher {

	private static final String SALT = "8f3b9a2c1d4e5f60";

	private final TextEncryptor encryptor;

	ExternalServiceSecretCipher(@Value("${app.integration.secret-key}") String secretKey) {
		this.encryptor = Encryptors.text(secretKey, SALT);
	}

	String encrypt(String plainText) {
		return encryptor.encrypt(plainText);
	}

	String decrypt(String cipherText) {
		return encryptor.decrypt(cipherText);
	}
}
