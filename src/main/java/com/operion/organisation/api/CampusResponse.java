package com.operion.organisation.api;

import com.operion.organisation.Campus;

public record CampusResponse(Long id, String name, String code, String addressLine1, String addressLine2, String city,
		String state, String pincode, String timezone, String status) {

	static CampusResponse from(Campus campus) {
		return new CampusResponse(campus.getId(), campus.getName(), campus.getCode(), campus.getAddressLine1(),
				campus.getAddressLine2(), campus.getCity(), campus.getState(), campus.getPincode(), campus.getTimezone(),
				campus.getStatus().name());
	}
}
