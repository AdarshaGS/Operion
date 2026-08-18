package com.operion.identity.auth.api;

public record ConfirmPasswordResetRequest(String organisationSlug, String token, String newPassword) {
}
