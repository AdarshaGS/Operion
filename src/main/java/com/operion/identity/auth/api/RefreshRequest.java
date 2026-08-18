package com.operion.identity.auth.api;

public record RefreshRequest(String organisationSlug, String refreshToken) {
}
