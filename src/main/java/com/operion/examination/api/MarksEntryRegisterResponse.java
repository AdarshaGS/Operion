package com.operion.examination.api;

import java.time.Instant;

import com.operion.examination.MarksEntryRegister;
import com.operion.examination.MarksEntryRegisterStatus;

public record MarksEntryRegisterResponse(Long id, Long examScheduleId, String registerStatus, Long approvedBy, Instant approvedAt) {

	static MarksEntryRegisterResponse from(MarksEntryRegister register) {
		boolean approved = register.getRegisterStatus() == MarksEntryRegisterStatus.APPROVED;
		return new MarksEntryRegisterResponse(register.getId(), register.getExamSchedule().getId(), register.getRegisterStatus().name(),
				approved ? register.getUpdatedBy() : null, approved ? register.getUpdatedAt() : null);
	}

	/** No register exists yet - marks entry hasn't started for this schedule. */
	static MarksEntryRegisterResponse notStarted(Long examScheduleId) {
		return new MarksEntryRegisterResponse(null, examScheduleId, MarksEntryRegisterStatus.DRAFT.name(), null, null);
	}
}
