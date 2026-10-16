package com.operion.organisation.api;

import jakarta.validation.constraints.NotBlank;

public record CreateCampusRequest(@NotBlank String name, @NotBlank String code, String addressLine1, String addressLine2, String city,
		String state, String pincode, String timezone) {
}
