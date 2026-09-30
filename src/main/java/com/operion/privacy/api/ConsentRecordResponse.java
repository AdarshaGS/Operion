package com.operion.privacy.api;

import java.time.Instant;

import com.operion.privacy.ConsentRecord;

public record ConsentRecordResponse(Long id, Long personId, Long purposeId, String purposeName, boolean granted,
		String source, String note, Instant recordedAt) {

	public static ConsentRecordResponse from(ConsentRecord record) {
		return new ConsentRecordResponse(record.getId(), record.getPerson().getId(), record.getPurpose().getId(),
				record.getPurpose().getName(), record.isGranted(), record.getSource().name(), record.getNote(),
				record.getRecordedAt());
	}
}
