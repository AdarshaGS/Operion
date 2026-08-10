package com.operion.examination.api;

import com.operion.examination.MarksEntry;

public record MarksEntryResponse(Long id, Long examScheduleId, Long studentEnrollmentId, Double marksObtained, boolean absent, String remarks) {

	static MarksEntryResponse from(MarksEntry entry) {
		return new MarksEntryResponse(entry.getId(), entry.getExamSchedule().getId(), entry.getStudentEnrollment().getId(),
				entry.getMarksObtained(), entry.isAbsent(), entry.getRemarks());
	}
}
