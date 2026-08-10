package com.operion.organisation.api;

public record CreateCampusRequest(String name, String code, String addressLine1, String addressLine2, String city,
		String state, String pincode, String timezone) {
}
