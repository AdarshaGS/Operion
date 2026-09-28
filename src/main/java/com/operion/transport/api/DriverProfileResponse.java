package com.operion.transport.api;

import java.time.LocalDate;

import com.operion.transport.DriverProfile;

public record DriverProfileResponse(Long id, Long personId, String licenceNumber, LocalDate licenceExpiryDate) {

	public static DriverProfileResponse from(DriverProfile profile) {
		return new DriverProfileResponse(profile.getId(), profile.getPerson().getId(), profile.getLicenceNumber(), profile.getLicenceExpiryDate());
	}
}
