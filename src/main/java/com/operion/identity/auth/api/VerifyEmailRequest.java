package com.operion.identity.auth.api;

public record VerifyEmailRequest(String organisationSlug, String token) {
}
