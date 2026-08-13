package com.operion.identity.auth.api;

public record ClaimInviteRequest(String organisationSlug, String token, String password) {
}
