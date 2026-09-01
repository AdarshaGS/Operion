package com.operion.integration.api;

/** Never carries the plaintext value - only whether the organisation has saved a
 * non-blank one, via {@code configured}. */
public record ExternalServicePropertyStatusResponse(String key, boolean secret, boolean configured) {
}
