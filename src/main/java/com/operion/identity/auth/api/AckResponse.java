package com.operion.identity.auth.api;

/** Minimal acknowledgement body for endpoints with no meaningful payload to return (logout,
 * password change/reset, email verification) - still returns the app's standard JSON shape
 * rather than a bare 204, so every client-side call goes through the same response.json()
 * path as everything else in web/src/api/client.ts. */
public record AckResponse(String message) {
}
