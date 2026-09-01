package com.operion.examination.api;

import java.time.Instant;

import com.operion.examination.MarksEntry;

public record MarksEntryResponse(Long id, Long examScheduleId, Long studentEnrollmentId, Double marksObtained, boolean absent, String remarks,
		boolean passed, Integer rank, boolean published, Long enteredBy, Instant enteredAt, Long correctedBy, Instant correctedAt) {

	static MarksEntryResponse from(MarksEntry entry, boolean published) {
		boolean wasCorrected = !entry.getUpdatedAt().equals(entry.getCreatedAt());
		boolean passed = !entry.isAbsent() && entry.getMarksObtained() != null && entry.getMarksObtained() >= entry.getExamSchedule().getPassMarks();
		return new MarksEntryResponse(entry.getId(), entry.getExamSchedule().getId(), entry.getStudentEnrollment().getId(),
				entry.getMarksObtained(), entry.isAbsent(), entry.getRemarks(), passed, entry.getRank(), published,
				entry.getCreatedBy(), entry.getCreatedAt(), wasCorrected ? entry.getUpdatedBy() : null, wasCorrected ? entry.getUpdatedAt() : null);
	}
}
