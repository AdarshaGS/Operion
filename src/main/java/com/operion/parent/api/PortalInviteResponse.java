package com.operion.parent.api;

import java.time.Instant;

public record PortalInviteResponse(Long inviteId, String claimToken, Instant expiresAt) {
}
