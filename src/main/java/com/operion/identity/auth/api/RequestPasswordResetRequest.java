package com.operion.identity.auth.api;

public record RequestPasswordResetRequest(String organisationSlug, String email) {
}
