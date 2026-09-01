package com.operion.integration;

/** One configurable field a service exposes, e.g. ("email.api-key", true). Whether it's
 * secret decides encryption at rest (see ExternalServiceSecretCipher) and whether its
 * value is ever returned to a client (see ExternalServicePropertyCatalog's callers). */
public record ExternalServicePropertyDefinition(String key, boolean secret) {
}
