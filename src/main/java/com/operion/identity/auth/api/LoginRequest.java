package com.operion.identity.auth.api;

public record LoginRequest(String organisationSlug, String email, String password) {
}
