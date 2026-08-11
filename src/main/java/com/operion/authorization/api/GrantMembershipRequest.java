package com.operion.authorization.api;

public record GrantMembershipRequest(Long userId, Long personId, Long roleId, Long campusId) {
}
