package com.operion.identity.api;

import java.time.Instant;

public record StaffInviteResponse(Long userId, Long inviteId, String claimToken, Instant expiresAt, boolean emailSent) {
}
