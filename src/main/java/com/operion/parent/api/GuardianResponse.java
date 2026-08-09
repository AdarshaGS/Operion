package com.operion.parent.api;

import com.operion.parent.Guardian;

public record GuardianResponse(Long id, Long personId, String occupation, String status) {

	static GuardianResponse from(Guardian guardian) {
		return new GuardianResponse(
				guardian.getId(), guardian.getPerson().getId(), guardian.getOccupation(), guardian.getStatus().name());
	}
}
