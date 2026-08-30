package com.operion.hr.api;

import java.time.LocalDate;

import com.operion.hr.StaffExit;

public record StaffExitResponse(Long id, Long staffProfileId, String exitType, LocalDate exitDate, String reason, Long initiatedBy) {

	public static StaffExitResponse from(StaffExit exit) {
		return new StaffExitResponse(exit.getId(), exit.getStaffProfile().getId(), exit.getExitType().name(),
				exit.getExitDate(), exit.getReason(), exit.getInitiatedBy());
	}
}
