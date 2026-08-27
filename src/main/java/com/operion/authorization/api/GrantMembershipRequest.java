package com.operion.authorization.api;

import java.time.LocalDate;

public record GrantMembershipRequest(Long userId, Long personId, Long roleId, Long campusId, Long departmentId, String memberId,
		LocalDate joiningDate) {
}
