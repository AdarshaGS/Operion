package com.operion.identity.auth.api;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
