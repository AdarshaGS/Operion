package com.operion.identity.auth.api;

public record ClaimStaffInviteRequest(String organisationSlug, String token, String password) {
}
