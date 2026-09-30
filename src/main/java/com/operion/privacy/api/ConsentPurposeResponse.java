package com.operion.privacy.api;

import com.operion.privacy.ConsentPurpose;

public record ConsentPurposeResponse(Long id, String code, String name, String description, String status) {

	public static ConsentPurposeResponse from(ConsentPurpose purpose) {
		return new ConsentPurposeResponse(purpose.getId(), purpose.getCode(), purpose.getName(), purpose.getDescription(),
				purpose.getStatus().name());
	}
}
